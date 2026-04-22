package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.app.ExecutionEvent
import org.slf4j.Logger

internal class DatabaseModuleRunEventLogSupport(
    runExecutionStore: DatabaseRunExecutionStore,
    objectMapper: ObjectMapper,
    logger: Logger,
) {
    private val payloadSupport = DatabaseModuleRunEventPayloadSupport(objectMapper)
    private val appendSupport = DatabaseModuleRunEventAppendSupport(
        runExecutionStore = runExecutionStore,
        logger = logger,
    )

    fun appendEvent(
        context: DatabaseModuleRunContext,
        stage: String,
        eventType: String,
        severity: String,
        sourceName: String?,
        message: String,
        event: ExecutionEvent,
    ) = appendSupport.appendEvent(
        context = context,
        stage = stage,
        eventType = eventType,
        severity = severity,
        sourceName = sourceName,
        message = message,
        payload = payloadSupport.createPayload(event),
    )
}
