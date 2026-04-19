package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.ConfigFormUpdateRequest
import com.sbrf.lt.platform.ui.model.ModulesCatalogResponse
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.model.SaveResultResponse
import com.sbrf.lt.platform.ui.model.UiRuntimeModeUpdateRequest
import com.sbrf.lt.platform.ui.model.UiRuntimeModeUpdateResponse
import com.sbrf.lt.platform.ui.model.toDiagnosticsResponse
import org.slf4j.Logger

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

internal fun UiServerContext.buildFilesModulesCatalogResponse(): ModulesCatalogResponse {
    val modules = filesModuleBackend.listModules()
    val activeModuleId = filesRunService.currentState().activeRun?.moduleId
    return ModulesCatalogResponse(
        appsRootStatus = requireNotNull(filesModuleBackend.catalogStatus()),
        diagnostics = modules.toDiagnosticsResponse(),
        modules = modules.map { module ->
            module.copy(hasActiveRun = module.id == activeModuleId)
        },
    )
}

internal fun UiServerContext.saveFilesModule(
    moduleId: String,
    request: SaveModuleRequest,
): SaveResultResponse {
    filesModuleBackend.saveModule(moduleId = moduleId, request = request)
    return SaveResultResponse("Изменения модуля сохранены.")
}

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
