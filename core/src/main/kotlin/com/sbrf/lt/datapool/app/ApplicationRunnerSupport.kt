package com.sbrf.lt.datapool.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.sbrf.lt.datapool.app.port.SourceExporter
import com.sbrf.lt.datapool.app.port.TargetImporter
import com.sbrf.lt.datapool.app.port.TargetSchemaValidator
import com.sbrf.lt.datapool.config.CredentialsFileLocator
import com.sbrf.lt.datapool.config.ProjectRootLocator
import com.sbrf.lt.datapool.config.ValueResolver
import com.sbrf.lt.datapool.model.AppConfig
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.datapool.model.ExportTask
import com.sbrf.lt.datapool.model.MergeSourceAllocation
import com.sbrf.lt.datapool.model.MergeSummary
import com.sbrf.lt.datapool.model.SourceExecutionResult
import com.sbrf.lt.datapool.model.SourceSummary
import com.sbrf.lt.datapool.model.SummaryReport
import com.sbrf.lt.datapool.model.TargetLoadSummary
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal class ApplicationRunnerSupport(
    private val exporter: SourceExporter,
    private val targetTableValidator: TargetSchemaValidator,
    private val importer: TargetImporter,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val summaryMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    fun defaultCredentialsPath(): Path? = CredentialsFileLocator.find()

    fun createOutputDir(outputRoot: String, startedAt: Instant): Path {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
        val outputRootPath = Path.of(outputRoot)
        val baseDir = if (outputRootPath.isAbsolute) {
            outputRootPath
        } else {
            (ProjectRootLocator.find() ?: Path.of("").toAbsolutePath().normalize()).resolve(outputRootPath).normalize()
        }
        val path = baseDir.resolve(formatter.format(startedAt))
        Files.createDirectories(path)
        return path
    }

    fun exportSources(
        appConfig: AppConfig,
        outputDir: Path,
        valueResolver: ValueResolver,
        executionListener: ExecutionListener,
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
                                queryTimeoutSec = appConfig.queryTimeoutSec,
                                progressLogEveryRows = appConfig.progressLogEveryRows,
                                executionListener = executionListener,
                            )
                        )
                    } catch (ex: Exception) {
                        logger.error("Источник {} завершился ошибкой до начала выгрузки: {}", source.name, ex.message, ex)
                        val finishedAt = Instant.now()
                        executionListener.onEvent(
                            SourceExportFinishedEvent(
                                timestamp = finishedAt,
                                sourceName = source.name,
                                status = ExecutionStatus.FAILED,
                                rowCount = 0,
                                columns = emptyList(),
                                outputFile = null,
                                errorMessage = ex.message ?: "Неизвестная ошибка",
                            )
                        )
                        SourceExecutionResult(
                            sourceName = source.name,
                            status = ExecutionStatus.FAILED,
                            rowCount = 0,
                            outputFile = null,
                            columns = emptyList(),
                            startedAt = startedAt,
                            finishedAt = finishedAt,
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

    fun filterSchemaMismatches(
        results: List<SourceExecutionResult>,
        executionListener: ExecutionListener,
    ): List<SourceExecutionResult> {
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
                executionListener.onEvent(
                    SourceSchemaMismatchEvent(
                        timestamp = Instant.now(),
                        sourceName = result.sourceName,
                        expectedColumns = baseline,
                        actualColumns = result.columns,
                    )
                )
                result.copy(
                    status = ExecutionStatus.SKIPPED_SCHEMA_MISMATCH,
                    outputFile = null,
                    errorMessage = "Несовпадение схемы. Ожидались колонки $baseline, получены ${result.columns}",
                )
            }
        }
    }

    fun writeSummary(
        outputDir: Path,
        results: List<SourceExecutionResult>,
        mergeResult: com.sbrf.lt.datapool.model.MergeResult,
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
            parallelism = config.parallelism,
            fetchSize = config.fetchSize,
            queryTimeoutSec = config.queryTimeoutSec,
            progressLogEveryRows = config.progressLogEveryRows,
            mergedRowCount = mergeResult.rowCount,
            mergedFile = mergedFile.fileName.toString(),
            maxMergedRows = config.maxMergedRows,
            targetEnabled = config.target.enabled,
            mergeDetails = buildMergeSummary(results, mergeResult),
            targetLoad = targetLoad,
            successfulSources = results.filter { it.status == ExecutionStatus.SUCCESS }.map(::toSummary),
            failedSources = results.filter { it.status != ExecutionStatus.SUCCESS }.map(::toSummary),
        )

        Files.newBufferedWriter(outputDir.resolve("summary.json")).use { writer ->
            summaryMapper.writerWithDefaultPrettyPrinter().writeValue(writer, summary)
        }
    }

    fun runTargetImport(
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

    fun cleanupGeneratedFiles(files: Set<Path>, executionListener: ExecutionListener) {
        files.forEach { path ->
            try {
                if (Files.deleteIfExists(path)) {
                    logger.info("Удален временный выходной файл {}", path.fileName)
                    executionListener.onEvent(
                        OutputCleanupEvent(
                            timestamp = Instant.now(),
                            fileName = path.fileName.toString(),
                        )
                    )
                }
            } catch (ex: Exception) {
                logger.warn("Не удалось удалить временный выходной файл {}: {}", path, ex.message)
            }
        }
    }

    private fun buildMergeSummary(
        results: List<SourceExecutionResult>,
        mergeResult: com.sbrf.lt.datapool.model.MergeResult,
    ): MergeSummary {
        val successful = results.filter { it.status == ExecutionStatus.SUCCESS }.associateBy { it.sourceName }
        val total = mergeResult.rowCount.toDouble().takeIf { it > 0 } ?: 1.0
        val allocations = successful.values.map { result ->
            val mergedRows = mergeResult.sourceCounts[result.sourceName] ?: 0L
            MergeSourceAllocation(
                sourceName = result.sourceName,
                availableRows = result.rowCount,
                mergedRows = mergedRows,
                mergedPercent = if (mergeResult.rowCount == 0L) 0.0 else (mergedRows * 100.0 / total),
            )
        }.sortedBy { it.sourceName }
        return MergeSummary(sourceAllocations = allocations)
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
}
