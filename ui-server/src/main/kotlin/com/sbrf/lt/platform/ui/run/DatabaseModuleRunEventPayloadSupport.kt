package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.app.ExecutionEvent

internal class DatabaseModuleRunEventPayloadSupport(
    private val objectMapper: ObjectMapper,
) {
    fun createPayload(event: ExecutionEvent): Map<String, Any?> {
        val payload = objectMapper.convertValue(event, MutableMap::class.java)
            .mapKeys { it.key.toString() }
            .toMutableMap()
        payload["type"] = event.javaClass.simpleName
        return payload
    }
}
