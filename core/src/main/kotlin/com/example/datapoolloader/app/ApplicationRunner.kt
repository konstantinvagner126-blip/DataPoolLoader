package com.example.datapoolloader.app

import com.example.datapoolloader.config.ConfigLoader
import com.example.datapoolloader.config.ValueResolver
import com.example.datapoolloader.db.PostgresExporter
import com.example.datapoolloader.db.PostgresImporter
import com.example.datapoolloader.db.TargetTableValidator
import com.example.datapoolloader.merge.MergeService
import com.example.datapoolloader.model.AppConfig
import com.example.datapoolloader.model.ExecutionStatus
import com.example.datapoolloader.model.ExportTask
import com.example.datapoolloader.model.SourceExecutionResult
import com.example.datapoolloader.model.SourceSummary
import com.example.datapoolloader.model.SummaryReport
import com.example.datapoolloader.model.TargetLoadSummary
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

class ApplicationRunner(
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val exporter: PostgresExporter = PostgresExporter(),
    private val mergeService: MergeService = MergeService(),
    private val targetTableValidator: TargetTableValidator = TargetTableValidator(),
    private val importer: PostgresImporter = PostgresImporter(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val summaryMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    fun run(configPath: Path, credentialsPath: Path? = defaultCredentialsPath()) {
        val appConfig = configLoader.load(configPath)
        val valueResolver = ValueResolver.fromFile(credentialsPath)
        val runStartedAt = Instant.now()
        val outputDir = createOutputDir(appConfig.outputDir, runStartedAt)
        logger.info("Используется выходная директория {}", outputDir)

        val results = exportSources(appConfig, outputDir, valueResolver)
        val filteredResults = filterSchemaMismatches(results)
        val successful = filteredResults.filter { it.status == ExecutionStatus.SUCCESS }
        require(successful.isNotEmpty()) { "Все источники завершились ошибкой. Файл merged.csv не был создан." }

        val mergedFile = outputDir.resolve("merged.csv")
        val mergedRowCount = mergeService.merge(successful, appConfig, mergedFile)
        val targetLoad = if (appConfig.target.enabled) {
            runTargetImport(appConfig, valueResolver, mergedFile, successful.first().columns, mergedRowCount)
        } else {
            logger.info("Загрузка в целевую БД отключена конфигурацией. Импорт пропускается.")
            TargetLoadSummary(
                table = appConfig.target.table.ifBlank { "<отключено>" },
                status = ExecutionStatus.SKIPPED,
                rowCount = 0,
                finishedAt = Instant.now(),
                enabled = false,
                errorMessage = "Загрузка в целевую БД отключена конфигурацией.",
            )
        }
        val runFinishedAt = Instant.now()
        writeSummary(
            outputDir = outputDir,
            results = filteredResults,
            mergedRowCount = mergedRowCount,
            mergedFile = mergedFile,
            targetLoad = targetLoad,
            startedAt = runStartedAt,
            finishedAt = runFinishedAt,
            config = appConfig,
        )
        require(!appConfig.target.enabled || targetLoad.status == ExecutionStatus.SUCCESS) {
            "Ошибка загрузки в целевую таблицу ${appConfig.target.table}: ${targetLoad.errorMessage}"
        }
        logger.info("Объединено {} строк в файл {}", mergedRowCount, mergedFile)
    }

    private fun exportSources(
        appConfig: AppConfig,
        outputDir: Path,
        valueResolver: ValueResolver,
    ): List<SourceExecutionResult> {
        val executor = Executors.newFixedThreadPool(appConfig.parallelism)
        return try {
            val futures = appConfig.sources.map { source ->
                executor.submit(Callable {
                    val startedAt = Instant.now()
                    try {
                        val sourceSql = source.sql ?: appConfig.commonSql
                        val resolvedJdbcUrl = valueResolver.resolve(source.jdbcUrl)
                        val resolvedUsername = valueResolver.resolve(source.username)
                        val resolvedPassword = valueResolver.resolve(source.password)
                        exporter.export(
                            ExportTask(
                                source = source,
                                resolvedJdbcUrl = resolvedJdbcUrl,
                                resolvedUsername = resolvedUsername,
                                resolvedPassword = resolvedPassword,
                                sql = sourceSql,
                                outputFile = outputDir.resolve("${source.name}.csv"),
                                fetchSize = appConfig.fetchSize,
                            )
                        )
                    } catch (ex: Exception) {
                        logger.error("Источник {} завершился ошибкой до начала выгрузки: {}", source.name, ex.message, ex)
                        SourceExecutionResult(
                            sourceName = source.name,
                            status = ExecutionStatus.FAILED,
                            rowCount = 0,
                            outputFile = null,
                            columns = emptyList(),
                            startedAt = startedAt,
                            finishedAt = Instant.now(),
                            errorMessage = ex.message ?: "Неизвестная ошибка",
                        )
                    }
                })
            }
            futures.map(Future<SourceExecutionResult>::get)
        } finally {
            executor.shutdown()
        }
    }

    private fun filterSchemaMismatches(results: List<SourceExecutionResult>): List<SourceExecutionResult> {
        val baseline = results.firstOrNull { it.status == ExecutionStatus.SUCCESS }?.columns ?: return results
        return results.map { result ->
            if (result.status != ExecutionStatus.SUCCESS) {
                result
            } else if (result.columns == baseline) {
                result
            } else {
                logger.error(
                    "Источник {} исключен из объединения: колонки {} не совпадают с базовым набором {}",
                    result.sourceName,
                    result.columns,
                    baseline,
                )
                result.copy(
                    status = ExecutionStatus.SKIPPED_SCHEMA_MISMATCH,
                    outputFile = null,
                    errorMessage = "Несовпадение схемы. Ожидались колонки $baseline, получены ${result.columns}",
                )
            }
        }
    }

    private fun writeSummary(
        outputDir: Path,
        results: List<SourceExecutionResult>,
        mergedRowCount: Long,
        mergedFile: Path,
        targetLoad: TargetLoadSummary,
        startedAt: Instant,
        finishedAt: Instant,
        config: AppConfig,
    ) {
        val summary = SummaryReport(
            startedAt = startedAt,
            finishedAt = finishedAt,
            mergeMode = config.mergeMode,
            mergedRowCount = mergedRowCount,
            mergedFile = mergedFile.fileName.toString(),
            targetLoad = targetLoad,
            successfulSources = results.filter { it.status == ExecutionStatus.SUCCESS }.map(::toSummary),
            failedSources = results.filter { it.status != ExecutionStatus.SUCCESS }.map(::toSummary),
        )

        Files.newBufferedWriter(outputDir.resolve("summary.json")).use { writer ->
            summaryMapper.writerWithDefaultPrettyPrinter().writeValue(writer, summary)
        }
    }

    private fun toSummary(result: SourceExecutionResult): SourceSummary = SourceSummary(
        sourceName = result.sourceName,
        status = result.status,
        rowCount = result.rowCount,
        outputFile = result.outputFile?.fileName?.toString(),
        columns = result.columns,
        startedAt = result.startedAt,
        finishedAt = result.finishedAt,
        errorMessage = result.errorMessage,
    )

    private fun createOutputDir(outputRoot: String, startedAt: Instant): Path {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
        val path = Path.of(outputRoot, formatter.format(startedAt))
        Files.createDirectories(path)
        return path
    }

    private fun runTargetImport(
        appConfig: AppConfig,
        valueResolver: ValueResolver,
        mergedFile: Path,
        columns: List<String>,
        mergedRowCount: Long,
    ): TargetLoadSummary {
        return try {
            val resolvedJdbcUrl = valueResolver.resolve(appConfig.target.jdbcUrl)
            val resolvedUsername = valueResolver.resolve(appConfig.target.username)
            val resolvedPassword = valueResolver.resolve(appConfig.target.password)
            targetTableValidator.validate(
                target = appConfig.target,
                resolvedJdbcUrl = resolvedJdbcUrl,
                resolvedUsername = resolvedUsername,
                resolvedPassword = resolvedPassword,
                incomingColumns = columns,
            )
            importer.importCsv(
                target = appConfig.target,
                resolvedJdbcUrl = resolvedJdbcUrl,
                resolvedUsername = resolvedUsername,
                mergedFile = mergedFile,
                columns = columns,
                expectedRowCount = mergedRowCount,
                resolvedPassword = resolvedPassword,
            )
        } catch (ex: Exception) {
            logger.error("Предварительная проверка целевой загрузки завершилась ошибкой: {}", ex.message, ex)
            TargetLoadSummary(
                table = appConfig.target.table,
                status = ExecutionStatus.FAILED,
                rowCount = mergedRowCount,
                finishedAt = Instant.now(),
                enabled = true,
                errorMessage = ex.message ?: "Неизвестная ошибка",
            )
        }
    }

    private fun defaultCredentialsPath(): Path? {
        val candidate = Path.of("gradle", "credential.properties")
        return if (Files.exists(candidate)) candidate else null
    }
}
