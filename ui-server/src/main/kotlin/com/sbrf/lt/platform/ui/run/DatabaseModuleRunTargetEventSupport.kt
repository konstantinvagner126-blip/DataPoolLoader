package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.TargetImportFinishedEvent
import com.sbrf.lt.datapool.app.TargetImportStartedEvent
import com.sbrf.lt.datapool.model.ExecutionStatus

internal class DatabaseModuleRunTargetEventSupport(
    private val runExecutionStore: DatabaseRunExecutionStore,
    private val eventLogSupport: DatabaseModuleRunEventLogSupport,
) {
    fun handleTargetStarted(context: DatabaseModuleRunContext, event: TargetImportStartedEvent) {
        context.targetStatus = "RUNNING"
        runExecutionStore.updateTargetStatus(context.runId, "RUNNING", event.table, null)
        eventLogSupport.appendEvent(
            context = context,
            stage = "TARGET",
            eventType = "TARGET_STARTED",
            severity = "INFO",
            sourceName = null,
            message = "Начата загрузка в target ${event.table}.",
            event = event,
        )
    }

    fun handleTargetFinished(context: DatabaseModuleRunContext, event: TargetImportFinishedEvent) {
        context.targetStatus = when (event.status) {
            ExecutionStatus.SUCCESS -> "SUCCESS"
            ExecutionStatus.SKIPPED -> "SKIPPED"
            else -> "FAILED"
        }
        context.targetRowsLoaded = event.rowCount
        runExecutionStore.updateTargetStatus(context.runId, context.targetStatus, event.table, event.rowCount)
        eventLogSupport.appendEvent(
            context = context,
            stage = "TARGET",
            eventType = if (context.targetStatus == "FAILED") "TARGET_FAILED" else "TARGET_FINISHED",
            severity = when (context.targetStatus) {
                "FAILED" -> "ERROR"
                "SUCCESS" -> "SUCCESS"
                else -> "INFO"
            },
            sourceName = null,
            message = if (context.targetStatus == "FAILED") {
                "Загрузка в target ${event.table} завершилась ошибкой: ${event.errorMessage ?: "неизвестная ошибка"}."
            } else {
                "Загрузка в target ${event.table} завершена."
            },
            event = event,
        )
    }
}
