package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import java.sql.PreparedStatement

internal fun createRunStoreObjectMapper(objectMapper: ObjectMapper): ObjectMapper =
    objectMapper
        .copy()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

internal fun setNullableLong(stmt: PreparedStatement, index: Int, value: Long?) {
    if (value == null) {
        stmt.setObject(index, null)
    } else {
        stmt.setLong(index, value)
    }
}

@Suppress("UNCHECKED_CAST")
internal fun readRunPayload(
    rawJson: String?,
    objectMapper: ObjectMapper,
): Map<String, Any?> {
    if (rawJson.isNullOrBlank()) {
        return emptyMap()
    }
    return objectMapper.readValue(rawJson, Map::class.java) as? Map<String, Any?> ?: emptyMap()
}
