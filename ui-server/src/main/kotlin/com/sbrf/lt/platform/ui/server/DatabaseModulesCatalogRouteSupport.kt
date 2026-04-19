package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.ui.model.toDiagnosticsResponse

internal fun UiServerContext.buildDatabaseModulesCatalogResponse(includeHidden: Boolean): DatabaseModulesCatalogResponse {
    requireDatabaseMaintenanceIsInactive()
    val runtimeContext = currentRuntimeContext()
    val store = currentDatabaseModuleStore()
    val modules = if (runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE && store != null) {
        val activeModuleCodes = runCatching {
            currentDatabaseModuleRunService()?.activeModuleCodes().orEmpty()
        }.getOrDefault(emptySet())
        currentDatabaseModuleBackend()
            ?.listModules(includeHidden = includeHidden)
            ?.map { module ->
                module.copy(hasActiveRun = module.id in activeModuleCodes)
            }
            .orEmpty()
    } else {
        emptyList()
    }
    return DatabaseModulesCatalogResponse(
        runtimeContext = runtimeContext,
        diagnostics = modules.toDiagnosticsResponse(),
        modules = modules,
    )
}
