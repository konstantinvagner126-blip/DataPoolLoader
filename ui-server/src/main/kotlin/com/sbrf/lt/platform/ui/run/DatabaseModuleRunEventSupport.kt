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
    private val startEventSupport = DatabaseModuleRunStartEventSupport(
        runExecutionStore = runExecutionStore,
        eventLogSupport = eventLogSupport,
    )
    private val eventRoutingSupport = DatabaseModuleRunEventRoutingSupport(
        artifactSupport = artifactSupport,
        sourceEventSupport = sourceEventSupport,
        mergeTargetEventSupport = mergeTargetEventSupport,
        completionSupport = completionSupport,
    )

    fun handleEvent(context: DatabaseModuleRunContext, event: ExecutionEvent) {
        synchronized(context) {
            when (event) {
                is com.sbrf.lt.datapool.app.RunStartedEvent -> startEventSupport.handleRunStarted(context, event)
                else -> eventRoutingSupport.handleEvent(context, event)
            }
        }
    }

    fun failRun(context: DatabaseModuleRunContext, exception: Throwable) =
        completionSupport.failRun(context, exception)
}
