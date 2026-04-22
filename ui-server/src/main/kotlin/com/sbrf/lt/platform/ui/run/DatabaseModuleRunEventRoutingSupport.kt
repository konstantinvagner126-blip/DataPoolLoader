package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.ExecutionEvent

internal class DatabaseModuleRunEventRoutingSupport(
    private val artifactSupport: DatabaseModuleRunArtifactSupport,
    private val sourceEventSupport: DatabaseModuleRunSourceEventSupport,
    private val mergeTargetEventSupport: DatabaseModuleRunMergeTargetEventSupport,
    private val completionSupport: DatabaseModuleRunCompletionSupport,
) {
    fun handleEvent(context: DatabaseModuleRunContext, event: ExecutionEvent) {
        when (event) {
            is com.sbrf.lt.datapool.app.SourceExportStartedEvent -> sourceEventSupport.handleSourceStarted(context, event)
            is com.sbrf.lt.datapool.app.SourceExportProgressEvent -> sourceEventSupport.handleSourceProgress(context, event)
            is com.sbrf.lt.datapool.app.SourceExportFinishedEvent -> sourceEventSupport.handleSourceFinished(context, event)
            is com.sbrf.lt.datapool.app.SourceSchemaMismatchEvent -> sourceEventSupport.handleSourceSchemaMismatch(context, event)
            is com.sbrf.lt.datapool.app.MergeStartedEvent -> mergeTargetEventSupport.handleMergeStarted(context, event)
            is com.sbrf.lt.datapool.app.MergeFinishedEvent -> mergeTargetEventSupport.handleMergeFinished(context, event)
            is com.sbrf.lt.datapool.app.TargetImportStartedEvent -> mergeTargetEventSupport.handleTargetStarted(context, event)
            is com.sbrf.lt.datapool.app.TargetImportFinishedEvent -> mergeTargetEventSupport.handleTargetFinished(context, event)
            is com.sbrf.lt.datapool.app.RunFinishedEvent -> completionSupport.handleRunFinished(context, event)
            is com.sbrf.lt.datapool.app.OutputCleanupEvent -> artifactSupport.markArtifactDeleted(context, event.fileName)
            else -> Unit
        }
    }
}
