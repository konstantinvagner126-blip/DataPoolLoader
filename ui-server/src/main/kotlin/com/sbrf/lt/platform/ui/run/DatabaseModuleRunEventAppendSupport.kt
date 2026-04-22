package com.sbrf.lt.platform.ui.run

import org.slf4j.Logger

internal class DatabaseModuleRunEventAppendSupport(
    private val runExecutionStore: DatabaseRunExecutionStore,
    private val logger: Logger,
) {
    fun appendEvent(
        context: DatabaseModuleRunContext,
        stage: String,
        eventType: String,
        severity: String,
        sourceName: String?,
        message: String,
        payload: Map<String, Any?>,
    ) {
        runCatching {
            runExecutionStore.appendEvent(
                runId = context.runId,
                seqNo = context.nextSeqNo++,
                stage = stage,
                eventType = eventType,
                severity = severity,
                sourceName = sourceName,
                message = message,
                payload = payload,
            )
        }.onFailure { ex ->
            logger.warn(
                "Не удалось записать DB run event: runId={}, stage={}, eventType={}, reason={}",
                context.runId,
                stage,
                eventType,
                ex.message,
            )
        }
    }
}
