package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.SourceExportFinishedEvent
import com.sbrf.lt.datapool.app.SourceExportProgressEvent
import com.sbrf.lt.datapool.app.SourceExportStartedEvent
import com.sbrf.lt.datapool.app.SourceSchemaMismatchEvent
import com.sbrf.lt.datapool.model.ExecutionStatus

internal class DatabaseModuleRunSourceEventSupport(
    private val runExecutionStore: DatabaseRunExecutionStore,
    private val artifactSupport: DatabaseModuleRunArtifactSupport,
    private val eventLogSupport: DatabaseModuleRunEventLogSupport,
) {
    fun handleSourceStarted(context: DatabaseModuleRunContext, event: SourceExportStartedEvent) {
        context.sourceStates.getOrPut(event.sourceName) { DatabaseRunSourceState() }.status = "RUNNING"
        runExecutionStore.markSourceStarted(context.runId, event.sourceName, event.timestamp)
        eventLogSupport.appendEvent(
            context = context,
            stage = "SOURCE",
            eventType = "SOURCE_STARTED",
            severity = "INFO",
            sourceName = event.sourceName,
            message = "Начата выгрузка источника ${event.sourceName}.",
            event = event,
        )
    }

    fun handleSourceProgress(context: DatabaseModuleRunContext, event: SourceExportProgressEvent) {
        val sourceState = context.sourceStates.getOrPut(event.sourceName) { DatabaseRunSourceState() }
        sourceState.status = "RUNNING"
        sourceState.exportedRowCount = event.rowCount
        runExecutionStore.updateSourceProgress(
            runId = context.runId,
            sourceName = event.sourceName,
            timestamp = event.timestamp,
            exportedRowCount = event.rowCount,
        )
        eventLogSupport.appendEvent(
            context = context,
            stage = "SOURCE",
            eventType = "SOURCE_PROGRESS",
            severity = "INFO",
            sourceName = event.sourceName,
            message = "Источник ${event.sourceName}: выгружено ${event.rowCount} строк.",
            event = event,
        )
    }

    fun handleSourceFinished(context: DatabaseModuleRunContext, event: SourceExportFinishedEvent) {
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
            artifactSupport.rememberArtifact(context, java.nio.file.Path.of(outputFile), "SOURCE_OUTPUT", event.sourceName)
        }
        eventLogSupport.appendEvent(
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

    fun handleSourceSchemaMismatch(context: DatabaseModuleRunContext, event: SourceSchemaMismatchEvent) {
        context.sourceStates.getOrPut(event.sourceName) { DatabaseRunSourceState() }.status = "SKIPPED"
        runExecutionStore.markSourceSkipped(
            runId = context.runId,
            sourceName = event.sourceName,
            finishedAt = event.timestamp,
            message = "Несовпадение схемы: ожидались ${event.expectedColumns}, получены ${event.actualColumns}",
        )
        eventLogSupport.appendEvent(
            context = context,
            stage = "SOURCE",
            eventType = "SOURCE_FAILED",
            severity = "WARNING",
            sourceName = event.sourceName,
            message = "Источник ${event.sourceName} исключен из merge из-за несовпадения схемы.",
            event = event,
        )
    }
}
