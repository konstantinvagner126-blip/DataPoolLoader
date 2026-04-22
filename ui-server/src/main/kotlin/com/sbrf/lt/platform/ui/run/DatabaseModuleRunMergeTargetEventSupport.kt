package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.MergeFinishedEvent
import com.sbrf.lt.datapool.app.MergeStartedEvent
import com.sbrf.lt.datapool.app.TargetImportFinishedEvent
import com.sbrf.lt.datapool.app.TargetImportStartedEvent

internal class DatabaseModuleRunMergeTargetEventSupport(
    runExecutionStore: DatabaseRunExecutionStore,
    artifactSupport: DatabaseModuleRunArtifactSupport,
    eventLogSupport: DatabaseModuleRunEventLogSupport,
) {
    private val mergeSupport = DatabaseModuleRunMergeEventSupport(
        runExecutionStore = runExecutionStore,
        artifactSupport = artifactSupport,
        eventLogSupport = eventLogSupport,
    )
    private val targetSupport = DatabaseModuleRunTargetEventSupport(
        runExecutionStore = runExecutionStore,
        eventLogSupport = eventLogSupport,
    )

    fun handleMergeStarted(context: DatabaseModuleRunContext, event: MergeStartedEvent) =
        mergeSupport.handleMergeStarted(context, event)

    fun handleMergeFinished(context: DatabaseModuleRunContext, event: MergeFinishedEvent) =
        mergeSupport.handleMergeFinished(context, event)

    fun handleTargetStarted(context: DatabaseModuleRunContext, event: TargetImportStartedEvent) =
        targetSupport.handleTargetStarted(context, event)

    fun handleTargetFinished(context: DatabaseModuleRunContext, event: TargetImportFinishedEvent) =
        targetSupport.handleTargetFinished(context, event)
}
