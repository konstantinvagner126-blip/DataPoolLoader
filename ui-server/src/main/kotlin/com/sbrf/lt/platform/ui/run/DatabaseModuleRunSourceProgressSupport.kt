package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.SourceExportProgressEvent
import com.sbrf.lt.datapool.app.SourceExportStartedEvent

internal class DatabaseModuleRunSourceProgressSupport(
    private val runExecutionStore: DatabaseRunExecutionStore,
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
}
