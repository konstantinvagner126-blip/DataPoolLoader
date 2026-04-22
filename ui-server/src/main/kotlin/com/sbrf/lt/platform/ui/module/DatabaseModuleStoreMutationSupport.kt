package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.model.RegistryModuleCreationResult
import com.sbrf.lt.datapool.db.registry.model.RegistryModuleDraft
import com.sbrf.lt.platform.ui.model.SaveModuleRequest

internal class DatabaseModuleStoreMutationSupport(
    connectionProvider: DatabaseConnectionProvider,
    schema: String,
    querySupport: DatabaseModuleStoreQuerySupport,
    lifecycleSupport: DatabaseModuleStoreLifecycleSupport,
    revisionWriter: DatabaseModuleRevisionWriter,
) {
    private val transactionSupport = DatabaseModuleStoreTransactionSupport(connectionProvider, schema)
    private val workingCopyMutationSupport = DatabaseModuleWorkingCopyMutationSupport(
        lifecycleSupport = lifecycleSupport,
        transactionSupport = transactionSupport,
    )
    private val revisionMutationSupport = DatabaseModuleRevisionMutationSupport(
        querySupport = querySupport,
        lifecycleSupport = lifecycleSupport,
        revisionWriter = revisionWriter,
        transactionSupport = transactionSupport,
    )

    fun saveWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        request: SaveModuleRequest,
    ) = workingCopyMutationSupport.saveWorkingCopy(moduleCode, actorId, actorSource, actorDisplayName, request)

    fun discardWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ) = workingCopyMutationSupport.discardWorkingCopy(moduleCode, actorId, actorSource)

    fun publishWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): PublishResult = revisionMutationSupport.publishWorkingCopy(moduleCode, actorId, actorSource, actorDisplayName)

    fun createModule(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        originKind: String,
        request: RegistryModuleDraft,
    ): RegistryModuleCreationResult = revisionMutationSupport.createModule(
        moduleCode,
        actorId,
        actorSource,
        actorDisplayName,
        originKind,
        request,
    )

    fun deleteModule(
        moduleCode: String,
        actorId: String,
    ): DeleteModuleResult = revisionMutationSupport.deleteModule(moduleCode, actorId)
}
