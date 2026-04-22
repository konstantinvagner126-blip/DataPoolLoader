package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.MergeFinishedEvent
import com.sbrf.lt.datapool.app.MergeStartedEvent

internal class DatabaseModuleRunMergeEventSupport(
    private val runExecutionStore: DatabaseRunExecutionStore,
    private val artifactSupport: DatabaseModuleRunArtifactSupport,
    private val eventLogSupport: DatabaseModuleRunEventLogSupport,
) {
    fun handleMergeStarted(context: DatabaseModuleRunContext, event: MergeStartedEvent) {
        eventLogSupport.appendEvent(
            context = context,
            stage = "MERGE",
            eventType = "MERGE_STARTED",
            severity = "INFO",
            sourceName = null,
            message = "Начато объединение результатов.",
            event = event,
        )
    }

    fun handleMergeFinished(context: DatabaseModuleRunContext, event: MergeFinishedEvent) {
        runExecutionStore.updateMergedRowCount(context.runId, event.rowCount)
        event.sourceCounts.forEach { (sourceName, mergedRowCount) ->
            context.sourceStates.getOrPut(sourceName) { DatabaseRunSourceState(status = "SUCCESS") }.mergedRowCount = mergedRowCount
            runExecutionStore.updateSourceMergedRows(context.runId, sourceName, mergedRowCount)
        }
        artifactSupport.rememberArtifact(context, java.nio.file.Path.of(event.outputFile), "MERGED_OUTPUT", "merged")
        eventLogSupport.appendEvent(
            context = context,
            stage = "MERGE",
            eventType = "MERGE_FINISHED",
            severity = "SUCCESS",
            sourceName = null,
            message = "Объединение завершено: ${event.rowCount} строк.",
            event = event,
        )
    }
}
