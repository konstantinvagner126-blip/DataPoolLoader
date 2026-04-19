package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.app.ExecutionEvent
import org.slf4j.Logger

internal class DatabaseModuleRunEventLogSupport(
    private val runExecutionStore: DatabaseRunExecutionStore,
    private val objectMapper: ObjectMapper,
    private val logger: Logger,
) {
    fun appendEvent(
        context: DatabaseModuleRunContext,
        stage: String,
        eventType: String,
        severity: String,
        sourceName: String?,
        message: String,
        event: ExecutionEvent,
    ) {
        runCatching {
            val payload = objectMapper.convertValue(event, MutableMap::class.java)
                .mapKeys { it.key.toString() }
                .toMutableMap()
            payload["type"] = event.javaClass.simpleName
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
