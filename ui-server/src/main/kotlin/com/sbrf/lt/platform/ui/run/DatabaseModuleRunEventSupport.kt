package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.app.ExecutionEvent
import com.sbrf.lt.datapool.model.ExecutionStatus
import org.slf4j.Logger
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.readText

internal class DatabaseModuleRunEventSupport(
    private val runExecutionStore: DatabaseRunExecutionStore,
    private val objectMapper: ObjectMapper,
    private val logger: Logger,
) {
    fun handleEvent(context: DatabaseModuleRunContext, event: ExecutionEvent) {
        synchronized(context) {
            when (event) {
                is com.sbrf.lt.datapool.app.RunStartedEvent -> {
                    runExecutionStore.createRun(context, event.timestamp, event.outputDir)
                    context.runCreated = true
                    appendEvent(context, "PREPARE", "RUN_CREATED", "INFO", null, "DB-запуск создан.", event)
                }
                is com.sbrf.lt.datapool.app.SourceExportStartedEvent -> {
                    context.sourceStates.getOrPut(event.sourceName) { DatabaseRunSourceState() }.status = "RUNNING"
                    runExecutionStore.markSourceStarted(context.runId, event.sourceName, event.timestamp)
                    appendEvent(context, "SOURCE", "SOURCE_STARTED", "INFO", event.sourceName, "Начата выгрузка источника ${event.sourceName}.", event)
                }
                is com.sbrf.lt.datapool.app.SourceExportProgressEvent -> {
                    val sourceState = context.sourceStates.getOrPut(event.sourceName) { DatabaseRunSourceState() }
                    sourceState.status = "RUNNING"
                    sourceState.exportedRowCount = event.rowCount
                    runExecutionStore.updateSourceProgress(
                        runId = context.runId,
                        sourceName = event.sourceName,
                        timestamp = event.timestamp,
                        exportedRowCount = event.rowCount,
                    )
                    appendEvent(
                        context = context,
                        stage = "SOURCE",
                        eventType = "SOURCE_PROGRESS",
                        severity = "INFO",
                        sourceName = event.sourceName,
                        message = "Источник ${event.sourceName}: выгружено ${event.rowCount} строк.",
                        event = event,
                    )
                }
                is com.sbrf.lt.datapool.app.SourceExportFinishedEvent -> {
                    val sourceState = context.sourceStates.getOrPut(event.sourceName) { DatabaseRunSourceState() }
                    sourceState.status = if (event.status == ExecutionStatus.SUCCESS) "SUCCESS" else "FAILED"
                    sourceState.exportedRowCount = event.rowCount
                    runExecutionStore.markSourceFinished(
                        runId = context.runId,
                        sourceName = event.sourceName,
                        status = sourceState.status,
                        finishedAt = event.timestamp,
                        exportedRowCount = event.rowCount,
                        errorMessage = event.errorMessage,
                    )
                    event.outputFile?.let { outputFile ->
                        rememberArtifact(context, Path.of(outputFile), "SOURCE_OUTPUT", event.sourceName)
                    }
                    appendEvent(
                        context = context,
                        stage = "SOURCE",
                        eventType = if (sourceState.status == "FAILED") "SOURCE_FAILED" else "SOURCE_FINISHED",
                        severity = if (sourceState.status == "FAILED") "ERROR" else "SUCCESS",
                        sourceName = event.sourceName,
                        message = if (sourceState.status == "FAILED") {
                            "Источник ${event.sourceName} завершился ошибкой: ${event.errorMessage ?: "неизвестная ошибка"}."
                        } else {
                            "Источник ${event.sourceName} выгружен: ${event.rowCount} строк."
                        },
                        event = event,
                    )
                }
                is com.sbrf.lt.datapool.app.SourceSchemaMismatchEvent -> {
                    context.sourceStates.getOrPut(event.sourceName) { DatabaseRunSourceState() }.status = "SKIPPED"
                    runExecutionStore.markSourceSkipped(
                        runId = context.runId,
                        sourceName = event.sourceName,
                        finishedAt = event.timestamp,
                        message = "Несовпадение схемы: ожидались ${event.expectedColumns}, получены ${event.actualColumns}",
                    )
                    appendEvent(
                        context,
                        "SOURCE",
                        "SOURCE_FAILED",
                        "WARNING",
                        event.sourceName,
                        "Источник ${event.sourceName} исключен из merge из-за несовпадения схемы.",
                        event,
                    )
                }
                is com.sbrf.lt.datapool.app.MergeStartedEvent -> {
                    appendEvent(context, "MERGE", "MERGE_STARTED", "INFO", null, "Начато объединение результатов.", event)
                }
                is com.sbrf.lt.datapool.app.MergeFinishedEvent -> {
                    runExecutionStore.updateMergedRowCount(context.runId, event.rowCount)
                    event.sourceCounts.forEach { (sourceName, mergedRowCount) ->
                        context.sourceStates.getOrPut(sourceName) { DatabaseRunSourceState(status = "SUCCESS") }.mergedRowCount = mergedRowCount
                        runExecutionStore.updateSourceMergedRows(context.runId, sourceName, mergedRowCount)
                    }
                    rememberArtifact(context, Path.of(event.outputFile), "MERGED_OUTPUT", "merged")
                    appendEvent(context, "MERGE", "MERGE_FINISHED", "SUCCESS", null, "Объединение завершено: ${event.rowCount} строк.", event)
                }
                is com.sbrf.lt.datapool.app.TargetImportStartedEvent -> {
                    context.targetStatus = "RUNNING"
                    runExecutionStore.updateTargetStatus(context.runId, "RUNNING", event.table, null)
                    appendEvent(context, "TARGET", "TARGET_STARTED", "INFO", null, "Начата загрузка в target ${event.table}.", event)
                }
                is com.sbrf.lt.datapool.app.TargetImportFinishedEvent -> {
                    context.targetStatus = when (event.status) {
                        ExecutionStatus.SUCCESS -> "SUCCESS"
                        ExecutionStatus.SKIPPED -> "SKIPPED"
                        else -> "FAILED"
                    }
                    context.targetRowsLoaded = event.rowCount
                    runExecutionStore.updateTargetStatus(context.runId, context.targetStatus, event.table, event.rowCount)
                    appendEvent(
                        context = context,
                        stage = "TARGET",
                        eventType = if (context.targetStatus == "FAILED") "TARGET_FAILED" else "TARGET_FINISHED",
                        severity = when (context.targetStatus) {
                            "FAILED" -> "ERROR"
                            "SUCCESS" -> "SUCCESS"
                            else -> "INFO"
                        },
                        sourceName = null,
                        message = if (context.targetStatus == "FAILED") {
                            "Загрузка в target ${event.table} завершилась ошибкой: ${event.errorMessage ?: "неизвестная ошибка"}."
                        } else {
                            "Загрузка в target ${event.table} завершена."
                        },
                        event = event,
                    )
                }
                is com.sbrf.lt.datapool.app.RunFinishedEvent -> {
                    val summaryJson = loadSummaryJson(event.summaryFile)
                    event.summaryFile?.let { rememberArtifact(context, Path.of(it), "SUMMARY_JSON", "summary") }
                    val successfulSourceCount = context.sourceStates.values.count { it.status == "SUCCESS" }
                    val failedSourceCount = context.sourceStates.values.count { it.status == "FAILED" }
                    val skippedSourceCount = context.sourceStates.values.count { it.status == "SKIPPED" }
                    val finalStatus = when {
                        event.status == ExecutionStatus.FAILED -> "FAILED"
                        failedSourceCount > 0 || skippedSourceCount > 0 -> "SUCCESS_WITH_WARNINGS"
                        else -> "SUCCESS"
                    }
                    runExecutionStore.finishRun(
                        runId = context.runId,
                        finishedAt = event.timestamp,
                        status = finalStatus,
                        mergedRowCount = event.mergedRowCount.takeIf { it > 0L },
                        successfulSourceCount = successfulSourceCount,
                        failedSourceCount = failedSourceCount,
                        skippedSourceCount = skippedSourceCount,
                        targetStatus = context.targetStatus,
                        targetTableName = context.runtimeSnapshot.appConfig.target.table.takeIf { it.isNotBlank() },
                        targetRowsLoaded = context.targetRowsLoaded,
                        summaryJson = summaryJson,
                        errorMessage = event.errorMessage,
                    )
                    appendEvent(
                        context = context,
                        stage = "RUN",
                        eventType = if (finalStatus == "FAILED") "RUN_FAILED" else "RUN_FINISHED",
                        severity = if (finalStatus == "FAILED") "ERROR" else "SUCCESS",
                        sourceName = null,
                        message = if (finalStatus == "FAILED") {
                            "DB-запуск завершился ошибкой: ${event.errorMessage ?: "неизвестная ошибка"}."
                        } else {
                            "DB-запуск завершен."
                        },
                        event = event,
                    )
                }
                is com.sbrf.lt.datapool.app.OutputCleanupEvent -> {
                    context.artifactRefsByFileName[event.fileName]?.let { ref ->
                        runExecutionStore.markArtifactDeleted(context.runId, ref.artifactKind, ref.artifactKey)
                    }
                }
            }
        }
    }

    fun failRun(context: DatabaseModuleRunContext, exception: Throwable) {
        if (!context.runCreated) {
            return
        }
        val errorMessage = exception.message
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: (exception::class.qualifiedName ?: "DB run failed")
        runCatching {
            runExecutionStore.markRunFailed(
                runId = context.runId,
                finishedAt = Instant.now(),
                errorMessage = errorMessage,
            )
        }.onFailure { markFailedError ->
            logger.error(
                "Не удалось перевести DB-run {} в FAILED после ошибки {}: {}",
                context.runId,
                errorMessage,
                markFailedError.message,
                markFailedError,
            )
        }
    }

    private fun rememberArtifact(context: DatabaseModuleRunContext, path: Path, artifactKind: String, artifactKey: String) {
        val storageStatus = if (path.exists()) "PRESENT" else "MISSING"
        val fileSize = if (path.exists()) runExecutionStore.fileSize(path) else null
        runExecutionStore.upsertArtifact(
            runId = context.runId,
            artifactKind = artifactKind,
            artifactKey = artifactKey,
            filePath = path.toString(),
            storageStatus = storageStatus,
            fileSizeBytes = fileSize,
            contentHash = null,
        )
        context.artifactRefsByFileName[path.fileName.toString()] = DatabaseRunArtifactRef(
            artifactKind = artifactKind,
            artifactKey = artifactKey,
        )
    }

    private fun appendEvent(
        context: DatabaseModuleRunContext,
        stage: String,
        eventType: String,
        severity: String,
        sourceName: String?,
        message: String,
        event: ExecutionEvent,
    ) {
        runCatching {
            val payload = objectMapper.convertValue(event, MutableMap::class.java)
                .mapKeys { it.key.toString() }
                .toMutableMap()
            payload["type"] = event.javaClass.simpleName
            runExecutionStore.appendEvent(
                runId = context.runId,
                seqNo = context.nextSeqNo++,
                stage = stage,
                eventType = eventType,
                severity = severity,
                sourceName = sourceName,
                message = message,
                payload = payload,
            )
        }.onFailure { ex ->
            logger.warn(
                "Не удалось записать DB run event: runId={}, stage={}, eventType={}, reason={}",
                context.runId,
                stage,
                eventType,
                ex.message,
            )
        }
    }

    private fun loadSummaryJson(summaryFile: String?): String {
        if (summaryFile.isNullOrBlank()) {
            return "{}"
        }
        val path = Path.of(summaryFile)
        if (!path.exists()) {
            return "{}"
        }
        return runCatching {
            objectMapper.readTree(path.readText()).toString()
        }.getOrDefault("{}")
    }
}
