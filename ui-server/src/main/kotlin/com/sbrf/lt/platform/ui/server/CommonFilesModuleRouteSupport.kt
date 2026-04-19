package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.ModulesCatalogResponse
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.model.SaveResultResponse
import com.sbrf.lt.platform.ui.model.toDiagnosticsResponse

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
