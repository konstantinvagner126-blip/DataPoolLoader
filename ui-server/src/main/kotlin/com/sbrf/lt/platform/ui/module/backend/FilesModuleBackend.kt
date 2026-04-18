package com.sbrf.lt.platform.ui.module.backend

import com.sbrf.lt.platform.ui.model.ModuleEditorSessionResponse
import com.sbrf.lt.platform.ui.model.ModuleLifecycleCapabilities
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.model.toCatalogItemResponse
import com.sbrf.lt.platform.ui.module.ModuleRegistry

/**
 * FILES-реализация backend-контрактов редактора модуля.
 */
class FilesModuleBackend(
    private val moduleRegistry: ModuleRegistry,
) : ModuleCatalogService, ModuleEditorStore, ModuleLifecycleService {
    override fun listModules(includeHidden: Boolean) = moduleRegistry.listModules(includeHidden).map { it.toCatalogItemResponse() }

    override fun catalogStatus() = moduleRegistry.appsRootStatus()

    override fun loadModule(moduleId: String, actor: ModuleActor?): ModuleEditorSessionResponse =
        ModuleEditorSessionResponse(
            storageMode = "FILES",
            module = moduleRegistry.loadModuleDetails(moduleId),
            capabilities = capabilities(),
            sourceKind = "FILES",
        )

    override fun saveModule(moduleId: String, request: SaveModuleRequest, actor: ModuleActor?) {
        moduleRegistry.saveModule(moduleId, request)
    }

    override fun capabilities(): ModuleLifecycleCapabilities =
        ModuleLifecycleCapabilities(
            save = true,
            run = true,
        )
}
