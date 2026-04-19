package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.SyncSelectedModulesRequest
import org.slf4j.Logger

internal fun UiServerContext.parseSyncSelectedModulesRequest(
    mapper: ObjectMapper,
    payload: String,
    logger: Logger,
): SyncSelectedModulesRequest =
    try {
        mapper.readValue(payload, SyncSelectedModulesRequest::class.java)
    } catch (error: Exception) {
        logger.warn("Некорректный payload для /api/db/sync/selected: {}", payload.take(4_000), error)
        badRequest("Некорректные данные для выборочной синхронизации.")
    }
