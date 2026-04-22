package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.RunStartedEvent

internal class DatabaseModuleRunStartEventSupport(
    private val runExecutionStore: DatabaseRunExecutionStore,
    private val eventLogSupport: DatabaseModuleRunEventLogSupport,
) {
    fun handleRunStarted(context: DatabaseModuleRunContext, event: RunStartedEvent) {
        runExecutionStore.createRun(context, event.timestamp, event.outputDir)
        context.runCreated = true
        eventLogSupport.appendEvent(
            context = context,
            stage = "PREPARE",
            eventType = "RUN_CREATED",
            severity = "INFO",
            sourceName = null,
            message = "DB-запуск создан.",
            event = event,
        )
    }
}
