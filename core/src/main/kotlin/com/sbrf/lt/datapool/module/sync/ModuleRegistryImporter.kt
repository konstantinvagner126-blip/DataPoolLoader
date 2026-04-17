package com.sbrf.lt.datapool.module.sync

import com.sbrf.lt.datapool.db.registry.model.RegistryModuleCreationResult
import com.sbrf.lt.datapool.db.registry.model.RegistryModuleDraft

/**
 * Контракт создания нового DB-модуля в registry для import-flow `files -> database`.
 */
interface ModuleRegistryImporter {
    fun createModule(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        originKind: String,
        draft: RegistryModuleDraft,
    ): RegistryModuleCreationResult
}
