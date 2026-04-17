package com.sbrf.lt.platform.ui.module.backend

import com.sbrf.lt.platform.ui.model.ModuleEditorSessionResponse
import com.sbrf.lt.platform.ui.model.ModuleLifecycleCapabilities
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.module.DatabaseModuleStore

/**
 * DATABASE-реализация backend-контрактов редактора модуля.
 */
class DatabaseModuleBackend(
    private val databaseModuleStore: DatabaseModuleStore,
) : ModuleCatalogService, ModuleEditorStore, ModuleLifecycleService {
    override fun listModules(includeHidden: Boolean) = databaseModuleStore.listModules(includeHidden)

    override fun loadModule(moduleId: String, actor: ModuleActor?): ModuleEditorSessionResponse {
        requireNotNull(actor) { "Для DATABASE storage нужен actor." }
        val editableModule = databaseModuleStore.loadModuleDetails(
            moduleCode = moduleId,
            actorId = actor.actorId,
            actorSource = actor.actorSource,
        )
        return ModuleEditorSessionResponse(
            storageMode = "DATABASE",
            module = editableModule.module,
            capabilities = capabilities(),
            sourceKind = editableModule.sourceKind,
            currentRevisionId = editableModule.currentRevisionId,
            workingCopyId = editableModule.workingCopyId,
            workingCopyStatus = editableModule.workingCopyStatus,
            baseRevisionId = editableModule.baseRevisionId,
        )
    }

    override fun saveModule(moduleId: String, request: SaveModuleRequest, actor: ModuleActor?) {
        requireNotNull(actor) { "Для DATABASE storage нужен actor." }
        databaseModuleStore.saveWorkingCopy(
            moduleCode = moduleId,
            actorId = actor.actorId,
            actorSource = actor.actorSource,
            actorDisplayName = actor.actorDisplayName,
            request = request,
        )
    }

    override fun capabilities(): ModuleLifecycleCapabilities =
        ModuleLifecycleCapabilities(
            saveWorkingCopy = true,
            discardWorkingCopy = true,
            publish = true,
            run = true,
            createModule = true,
            deleteModule = true,
        )
}
