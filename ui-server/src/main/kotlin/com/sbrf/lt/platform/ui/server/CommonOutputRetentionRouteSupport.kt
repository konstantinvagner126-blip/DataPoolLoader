package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.model.output.OutputRetentionPreviewResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionRequest
import com.sbrf.lt.platform.ui.model.output.OutputRetentionResultResponse

internal fun UiServerContext.previewCommonOutputRetention(
    runtimeContext: UiRuntimeContext,
    disableSafeguard: Boolean,
): OutputRetentionPreviewResponse =
    when (runtimeContext.requestedMode) {
        UiModuleStoreMode.FILES -> currentFilesOutputRetentionService().previewCleanup(disableSafeguard)
        UiModuleStoreMode.DATABASE -> {
            requireDatabaseMaintenanceIsInactive()
            requireDatabaseMode(runtimeContext)
            requireNotNull(currentDatabaseOutputRetentionService()) {
                "Сервис retention output-каталогов для режима базы данных не настроен."
            }.previewCleanup(disableSafeguard)
        }
    }

internal fun UiServerContext.executeCommonOutputRetention(
    runtimeContext: UiRuntimeContext,
    disableSafeguard: Boolean,
): OutputRetentionResultResponse =
    when (runtimeContext.requestedMode) {
        UiModuleStoreMode.FILES -> currentFilesOutputRetentionService().executeCleanup(disableSafeguard)
        UiModuleStoreMode.DATABASE -> {
            requireDatabaseMaintenanceIsInactive()
            requireDatabaseMode(runtimeContext)
            requireNotNull(currentDatabaseOutputRetentionService()) {
                "Сервис retention output-каталогов для режима базы данных не настроен."
            }.executeCleanup(disableSafeguard)
        }
    }

internal fun UiServerContext.parseCommonOutputRetentionRequest(
    mapper: ObjectMapper,
    payload: String,
): OutputRetentionRequest =
    try {
        if (payload.isBlank()) {
            OutputRetentionRequest()
        } else {
            mapper.readValue(payload, OutputRetentionRequest::class.java)
        }
    } catch (_: Exception) {
        badRequest("Некорректные данные для retention output-каталогов.")
    }
