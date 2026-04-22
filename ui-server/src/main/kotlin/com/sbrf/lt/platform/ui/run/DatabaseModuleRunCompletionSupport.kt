package com.sbrf.lt.platform.ui.run

import org.slf4j.Logger

internal class DatabaseModuleRunCompletionSupport(
    runExecutionStore: DatabaseRunExecutionStore,
    artifactSupport: DatabaseModuleRunArtifactSupport,
    eventLogSupport: DatabaseModuleRunEventLogSupport,
    logger: Logger,
) {
    private val successSupport = DatabaseModuleRunSuccessSupport(
        runExecutionStore = runExecutionStore,
        artifactSupport = artifactSupport,
        eventLogSupport = eventLogSupport,
    )
    private val failureSupport = DatabaseModuleRunFailureSupport(
        runExecutionStore = runExecutionStore,
        logger = logger,
    )

    fun handleRunFinished(context: DatabaseModuleRunContext, event: com.sbrf.lt.datapool.app.RunFinishedEvent) =
        successSupport.handleRunFinished(context, event)

    fun failRun(context: DatabaseModuleRunContext, exception: Throwable) =
        failureSupport.failRun(context, exception)
}
