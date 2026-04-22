package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.db.registry.model.RegistryModuleDraft
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import java.sql.Connection

internal class DatabaseModuleStoreLifecycleSupport(
    private val lookupSupport: DatabaseModuleStoreLookupSupport,
    private val persistenceSupport: DatabaseModuleStorePersistenceSupport,
) {
    fun loadModuleForSave(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): DatabaseModuleForSave = lookupSupport.loadModuleForSave(connection, normalizedSchema, moduleCode, actorId, actorSource)

    fun loadModuleForPublish(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): ModuleForPublish = lookupSupport.loadModuleForPublish(connection, normalizedSchema, moduleCode, actorId, actorSource)

    fun upsertWorkingCopy(
        connection: Connection,
        normalizedSchema: String,
        module: DatabaseModuleForSave,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        request: SaveModuleRequest,
    ) = persistenceSupport.upsertWorkingCopy(connection, normalizedSchema, module, actorId, actorSource, actorDisplayName, request)

    fun updateModuleCurrentRevision(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
        revisionId: String,
    ) = persistenceSupport.updateModuleCurrentRevision(connection, normalizedSchema, moduleId, revisionId)

    fun deleteWorkingCopyAfterPublish(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
        actorId: String,
        actorSource: String,
    ) = persistenceSupport.deleteWorkingCopyAfterPublish(connection, normalizedSchema, moduleId, actorId, actorSource)

    fun loadModuleForDelete(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
    ): ModuleForDelete = lookupSupport.loadModuleForDelete(connection, normalizedSchema, moduleCode)

    fun insertNewModule(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
        moduleCode: String,
        originKind: String,
    ) = persistenceSupport.insertNewModule(connection, normalizedSchema, moduleId, moduleCode, originKind)

    fun insertInitialWorkingCopy(
        connection: Connection,
        normalizedSchema: String,
        workingCopyId: String,
        moduleId: String,
        revisionId: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        request: RegistryModuleDraft,
    ) = persistenceSupport.insertInitialWorkingCopy(
        connection,
        normalizedSchema,
        workingCopyId,
        moduleId,
        revisionId,
        actorId,
        actorSource,
        actorDisplayName,
        request,
    )

    fun deleteWorkingCopyForModule(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
    ) = persistenceSupport.deleteWorkingCopyForModule(connection, normalizedSchema, moduleId)

    fun deleteModuleCascade(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
    ) = persistenceSupport.deleteModuleCascade(connection, normalizedSchema, moduleId)
}
