package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.RunFinishedEvent
import com.sbrf.lt.datapool.model.ExecutionStatus
import org.slf4j.Logger
import java.time.Instant

internal class DatabaseModuleRunCompletionSupport(
    private val runExecutionStore: DatabaseRunExecutionStore,
    private val artifactSupport: DatabaseModuleRunArtifactSupport,
    private val eventLogSupport: DatabaseModuleRunEventLogSupport,
    private val logger: Logger,
) {
    fun handleRunFinished(context: DatabaseModuleRunContext, event: RunFinishedEvent) {
        val summaryJson = artifactSupport.loadSummaryJson(event.summaryFile)
        event.summaryFile?.let {
            artifactSupport.rememberArtifact(context, java.nio.file.Path.of(it), "SUMMARY_JSON", "summary")
        }
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
        eventLogSupport.appendEvent(
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
}
