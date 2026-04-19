package com.sbrf.lt.platform.ui.sync

import com.sbrf.lt.datapool.db.registry.model.RegistryModuleCreationResult
import com.sbrf.lt.datapool.db.registry.model.RegistryModuleDraft
import com.sbrf.lt.datapool.module.sync.ModuleRegistryImporter
import com.sbrf.lt.platform.ui.module.DatabaseModuleRegistryOperations

/**
 * UI-адаптер, который использует DB-registry контракт как реализацию импорта в registry.
 */
class DatabaseModuleSyncImporter(
    private val databaseModuleStore: DatabaseModuleRegistryOperations,
) : ModuleRegistryImporter {
    override fun createModule(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        originKind: String,
        draft: RegistryModuleDraft,
    ): RegistryModuleCreationResult {
        val result = databaseModuleStore.createModule(
            moduleCode = moduleCode,
            actorId = actorId,
            actorSource = actorSource,
            actorDisplayName = actorDisplayName,
            originKind = originKind,
            request = draft,
        )
        return RegistryModuleCreationResult(
            moduleId = result.moduleId,
            moduleCode = result.moduleCode,
            revisionId = result.revisionId,
            workingCopyId = result.workingCopyId,
        )
    }
}
