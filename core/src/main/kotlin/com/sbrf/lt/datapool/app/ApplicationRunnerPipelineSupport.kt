package com.sbrf.lt.datapool.app

import com.sbrf.lt.datapool.app.port.ResultMerger
import com.sbrf.lt.datapool.config.ValueResolver
import com.sbrf.lt.datapool.model.AppConfig
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.datapool.model.SourceExecutionResult
import com.sbrf.lt.datapool.model.TargetLoadSummary
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

internal class ApplicationRunnerPipelineSupport(
    private val mergeService: ResultMerger,
    private val support: ApplicationRunnerSupport,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun runSnapshot(
        snapshot: RuntimeModuleSnapshot,
        credentialsPath: Path?,
        executionListener: ExecutionListener,
    ): ApplicationRunResult {
        val appConfig = snapshot.appConfig
        val valueResolver = ValueResolver.fromFile(credentialsPath)
        val runStartedAt = Instant.now()
        val outputDir = support.createOutputDir(appConfig.outputDir, runStartedAt)
        val generatedFiles = linkedSetOf<Path>()
        logger.info("Используется выходная директория {}", outputDir)
        executionListener.onEvent(
            RunStartedEvent(
                timestamp = runStartedAt,
                configPath = snapshot.configLocation,
                outputDir = outputDir.toString(),
                sourceNames = appConfig.sources.map { it.name },
                mergeMode = appConfig.mergeMode,
                targetEnabled = appConfig.target.enabled,
            ),
        )

        try {
            val results = support.exportSources(appConfig, outputDir, valueResolver, executionListener)
            generatedFiles.addAll(results.mapNotNull { it.outputFile })
            val filteredResults = support.filterSchemaMismatches(results, executionListener)
            val successful = filteredResults.filter { it.status == ExecutionStatus.SUCCESS }
            require(successful.isNotEmpty()) { "Все источники завершились ошибкой. Файл merged.csv не был создан." }

            val mergedFile = outputDir.resolve("merged.csv")
            executionListener.onEvent(
                MergeStartedEvent(
                    timestamp = Instant.now(),
                    mergeMode = appConfig.mergeMode,
                    sourceNames = successful.map { it.sourceName },
                    outputFile = mergedFile.toString(),
                ),
            )
            val mergeResult = mergeService.merge(successful, appConfig, mergedFile)
            generatedFiles.add(mergedFile)
            executionListener.onEvent(
                MergeFinishedEvent(
                    timestamp = Instant.now(),
                    rowCount = mergeResult.rowCount,
                    outputFile = mergedFile.toString(),
                    sourceCounts = mergeResult.sourceCounts,
                ),
            )

            val targetLoad = runTargetStage(appConfig, valueResolver, mergedFile, successful, mergeResult.rowCount, executionListener)
            val runFinishedAt = Instant.now()
            val summaryFile = outputDir.resolve("summary.json")
            support.writeSummary(
                outputDir = outputDir,
                results = filteredResults,
                mergeResult = mergeResult,
                mergedFile = mergedFile,
                targetLoad = targetLoad,
                startedAt = runStartedAt,
                finishedAt = runFinishedAt,
                config = appConfig,
            )
            require(!appConfig.target.enabled || targetLoad.status == ExecutionStatus.SUCCESS) {
                "Ошибка загрузки в целевую таблицу ${appConfig.target.table}: ${targetLoad.errorMessage}"
            }
            logger.info("Объединено {} строк в файл {}", mergeResult.rowCount, mergedFile)
            executionListener.onEvent(
                RunFinishedEvent(
                    timestamp = runFinishedAt,
                    status = ExecutionStatus.SUCCESS,
                    mergedRowCount = mergeResult.rowCount,
                    outputDir = outputDir.toString(),
                    summaryFile = summaryFile.toString(),
                ),
            )
            return ApplicationRunResult(
                status = ExecutionStatus.SUCCESS,
                outputDir = outputDir,
                mergedRowCount = mergeResult.rowCount,
                summaryFile = summaryFile,
            )
        } catch (ex: Exception) {
            val finishedAt = Instant.now()
            executionListener.onEvent(
                RunFinishedEvent(
                    timestamp = finishedAt,
                    status = ExecutionStatus.FAILED,
                    mergedRowCount = 0,
                    outputDir = outputDir.toString(),
                    summaryFile = outputDir.resolve("summary.json").takeIf { Files.exists(it) }?.toString(),
                    errorMessage = ex.message ?: "Неизвестная ошибка",
                ),
            )
            throw ex
        } finally {
            if (appConfig.deleteOutputFilesAfterCompletion) {
                support.cleanupGeneratedFiles(generatedFiles, executionListener)
            }
        }
    }

    private fun runTargetStage(
        appConfig: AppConfig,
        valueResolver: ValueResolver,
        mergedFile: Path,
        successful: List<SourceExecutionResult>,
        mergedRowCount: Long,
        executionListener: ExecutionListener,
    ): TargetLoadSummary {
        return if (appConfig.target.enabled) {
            executionListener.onEvent(
                TargetImportStartedEvent(
                    timestamp = Instant.now(),
                    table = appConfig.target.table,
                    expectedRowCount = mergedRowCount,
                ),
            )
            support.runTargetImport(
                appConfig = appConfig,
                valueResolver = valueResolver,
                mergedFile = mergedFile,
                columns = successful.first().columns,
                mergedRowCount = mergedRowCount,
            )
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
        }.also { targetLoad ->
            executionListener.onEvent(
                TargetImportFinishedEvent(
                    timestamp = Instant.now(),
                    table = targetLoad.table,
                    status = targetLoad.status,
                    rowCount = targetLoad.rowCount,
                    errorMessage = targetLoad.errorMessage,
                ),
            )
        }
    }
}
