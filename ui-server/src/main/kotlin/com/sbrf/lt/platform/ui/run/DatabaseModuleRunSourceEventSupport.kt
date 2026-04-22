package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.SourceExportFinishedEvent
import com.sbrf.lt.datapool.app.SourceExportProgressEvent
import com.sbrf.lt.datapool.app.SourceExportStartedEvent
import com.sbrf.lt.datapool.app.SourceSchemaMismatchEvent

internal class DatabaseModuleRunSourceEventSupport(
    runExecutionStore: DatabaseRunExecutionStore,
    artifactSupport: DatabaseModuleRunArtifactSupport,
    eventLogSupport: DatabaseModuleRunEventLogSupport,
) {
    private val progressSupport = DatabaseModuleRunSourceProgressSupport(
        runExecutionStore = runExecutionStore,
        eventLogSupport = eventLogSupport,
    )
    private val completionSupport = DatabaseModuleRunSourceCompletionEventSupport(
        runExecutionStore = runExecutionStore,
        artifactSupport = artifactSupport,
        eventLogSupport = eventLogSupport,
    )

    fun handleSourceStarted(context: DatabaseModuleRunContext, event: SourceExportStartedEvent) =
        progressSupport.handleSourceStarted(context, event)

    fun handleSourceProgress(context: DatabaseModuleRunContext, event: SourceExportProgressEvent) =
        progressSupport.handleSourceProgress(context, event)

    fun handleSourceFinished(context: DatabaseModuleRunContext, event: SourceExportFinishedEvent) =
        completionSupport.handleSourceFinished(context, event)

    fun handleSourceSchemaMismatch(context: DatabaseModuleRunContext, event: SourceSchemaMismatchEvent) =
        completionSupport.handleSourceSchemaMismatch(context, event)
}
