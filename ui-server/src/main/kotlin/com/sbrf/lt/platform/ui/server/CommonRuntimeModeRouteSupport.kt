package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.UiRuntimeModeUpdateRequest
import com.sbrf.lt.platform.ui.model.UiRuntimeModeUpdateResponse

internal fun UiServerContext.updateCommonRuntimeMode(
    request: UiRuntimeModeUpdateRequest,
): UiRuntimeModeUpdateResponse {
    val updatedConfig = uiConfigPersistenceService.updateModuleStoreMode(request.mode)
    val updatedRuntimeContext = resolveRuntimeContextFromConfig(updatedConfig)
    return UiRuntimeModeUpdateResponse(
        message = "Предпочитаемый режим UI сохранен: ${request.mode.toConfigValue()}.",
        runtimeContext = updatedRuntimeContext,
    )
}
