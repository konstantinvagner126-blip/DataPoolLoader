package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupRequest
import org.slf4j.Logger

internal fun UiServerContext.parseDatabaseRunHistoryCleanupRequest(
    mapper: ObjectMapper,
    payload: String,
    logger: Logger,
): DatabaseRunHistoryCleanupRequest =
    try {
        if (payload.isBlank()) {
            DatabaseRunHistoryCleanupRequest()
        } else {
            mapper.readValue(payload, DatabaseRunHistoryCleanupRequest::class.java)
        }
    } catch (error: Exception) {
        logger.warn("Некорректный payload для /api/db/run-history/cleanup: {}", payload.take(4_000), error)
        badRequest("Некорректные данные для cleanup истории запусков.")
    }
