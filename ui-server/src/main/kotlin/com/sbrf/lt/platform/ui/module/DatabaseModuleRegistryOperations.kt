package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.platform.ui.model.ModuleCatalogItemResponse
import com.sbrf.lt.platform.ui.model.SaveModuleRequest

/**
 * Контракт DB-registry операций для server-layer и UI-адаптеров.
 */
interface DatabaseModuleRegistryOperations {
    fun listModules(includeHidden: Boolean = false): List<ModuleCatalogItemResponse>

    fun loadModuleDetails(
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): DatabaseEditableModule

    fun saveWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        request: SaveModuleRequest,
    )

    fun discardWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
    )

    fun publishWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): PublishResult

    fun createModule(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        originKind: String = "CREATED_IN_UI",
        request: CreateModuleRequest,
    ): CreateModuleResult

    fun deleteModule(
        moduleCode: String,
        actorId: String,
    ): DeleteModuleResult
}
