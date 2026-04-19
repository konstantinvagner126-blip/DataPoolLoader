package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.ConfigFormUpdateRequest
import org.slf4j.Logger

internal fun UiServerContext.applyCommonConfigFormUpdate(
    mapper: ObjectMapper,
    payload: String,
    logger: Logger,
) = parseCommonConfigFormUpdateRequest(mapper, payload, logger).let { request ->
    configFormService.apply(request.configText, request.formState)
}

private fun UiServerContext.parseCommonConfigFormUpdateRequest(
    mapper: ObjectMapper,
    payload: String,
    logger: Logger,
): ConfigFormUpdateRequest =
    try {
        mapper.readValue(payload, ConfigFormUpdateRequest::class.java)
    } catch (error: Exception) {
        logger.warn("Некорректный payload для /api/config-form/update: {}", payload.take(4_000), error)
        val rootCauseMessage = generateSequence<Throwable>(error) { it.cause }
            .lastOrNull()
            ?.message
            ?.takeIf { it.isNotBlank() }
        badRequest(
            buildString {
                append("Некорректные данные формы настроек.")
                if (!rootCauseMessage.isNullOrBlank()) {
                    append(" ")
                    append(rootCauseMessage)
                }
            },
        )
    }
