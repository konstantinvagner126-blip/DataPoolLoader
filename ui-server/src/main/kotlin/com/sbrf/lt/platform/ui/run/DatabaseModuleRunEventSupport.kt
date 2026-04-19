package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.app.ExecutionEvent
import org.slf4j.Logger

internal class DatabaseModuleRunEventSupport(
    private val runExecutionStore: DatabaseRunExecutionStore,
    private val objectMapper: ObjectMapper,
    private val logger: Logger,
) {
    private val artifactSupport = DatabaseModuleRunArtifactSupport(runExecutionStore)
    private val eventLogSupport = DatabaseModuleRunEventLogSupport(
        runExecutionStore = runExecutionStore,
        objectMapper = objectMapper,
        logger = logger,
    )
    private val completionSupport = DatabaseModuleRunCompletionSupport(
        runExecutionStore = runExecutionStore,
        artifactSupport = artifactSupport,
        eventLogSupport = eventLogSupport,
        logger = logger,
    )
    private val sourceEventSupport = DatabaseModuleRunSourceEventSupport(
        runExecutionStore = runExecutionStore,
        artifactSupport = artifactSupport,
        eventLogSupport = eventLogSupport,
    )
    private val mergeTargetEventSupport = DatabaseModuleRunMergeTargetEventSupport(
        runExecutionStore = runExecutionStore,
        artifactSupport = artifactSupport,
        eventLogSupport = eventLogSupport,
    )

    fun handleEvent(context: DatabaseModuleRunContext, event: ExecutionEvent) {
        synchronized(context) {
            when (event) {
                is com.sbrf.lt.datapool.app.RunStartedEvent -> {
                    runExecutionStore.createRun(context, event.timestamp, event.outputDir)
                    context.runCreated = true
                    eventLogSupport.appendEvent(context, "PREPARE", "RUN_CREATED", "INFO", null, "DB-запуск создан.", event)
                }
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
            }
        }
    }

    fun failRun(context: DatabaseModuleRunContext, exception: Throwable) =
        completionSupport.failRun(context, exception)
}
