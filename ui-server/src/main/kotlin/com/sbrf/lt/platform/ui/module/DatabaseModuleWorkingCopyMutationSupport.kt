package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.platform.ui.model.SaveModuleRequest

/**
 * Mutation flow для personal working copy DB-модуля.
 */
internal class DatabaseModuleWorkingCopyMutationSupport(
    private val lifecycleSupport: DatabaseModuleStoreLifecycleSupport,
    private val transactionSupport: DatabaseModuleStoreTransactionSupport,
) {
    fun saveWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        request: SaveModuleRequest,
    ) {
        transactionSupport.withTransaction { connection, normalizedSchema ->
            val module = lifecycleSupport.loadModuleForSave(
                connection = connection,
                normalizedSchema = normalizedSchema,
                moduleCode = moduleCode,
                actorId = actorId,
                actorSource = actorSource,
            )
            lifecycleSupport.upsertWorkingCopy(
                connection = connection,
                normalizedSchema = normalizedSchema,
                module = module,
                actorId = actorId,
                actorSource = actorSource,
                actorDisplayName = actorDisplayName,
                request = request,
            )
        }
    }

    fun discardWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ) {
        transactionSupport.discardWorkingCopy(moduleCode, actorId, actorSource)
    }
}
