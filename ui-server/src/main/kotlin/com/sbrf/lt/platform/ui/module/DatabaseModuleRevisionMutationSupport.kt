package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.db.registry.model.RegistryModuleCreationResult
import com.sbrf.lt.datapool.db.registry.model.RegistryModuleDraft

/**
 * Mutation flow для публикации, создания и удаления DB-модулей.
 */
internal class DatabaseModuleRevisionMutationSupport(
    private val querySupport: DatabaseModuleStoreQuerySupport,
    private val lifecycleSupport: DatabaseModuleStoreLifecycleSupport,
    private val revisionWriter: DatabaseModuleRevisionWriter,
    private val transactionSupport: DatabaseModuleStoreTransactionSupport,
) {
    fun publishWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): PublishResult = transactionSupport.withTransaction { connection, normalizedSchema ->
        val moduleInfo = lifecycleSupport.loadModuleForPublish(
            connection = connection,
            normalizedSchema = normalizedSchema,
            moduleCode = moduleCode,
            actorId = actorId,
            actorSource = actorSource,
        )
        require(moduleInfo.hasWorkingCopy) {
            "Нет личного черновика для публикации. Сначала сохраните изменения."
        }
        require(moduleInfo.baseRevisionId == moduleInfo.currentRevisionId && moduleInfo.workingCopyStatus != "STALE") {
            "Личный черновик устарел относительно текущей ревизии. Перезагрузите модуль и примените изменения заново."
        }

        val newRevisionId = java.util.UUID.randomUUID().toString()
        val newRevisionNo = moduleInfo.maxRevisionNo + 1

        revisionWriter.insertPublishedRevision(
            connection = connection,
            normalizedSchema = normalizedSchema,
            revisionId = newRevisionId,
            moduleId = moduleInfo.moduleId,
            currentRevisionId = moduleInfo.currentRevisionId,
            revisionNo = newRevisionNo,
            actorId = actorId,
            actorDisplayName = actorDisplayName,
            workingCopyJson = moduleInfo.workingCopyJson!!,
            workingCopyYaml = moduleInfo.workingCopyYaml!!,
            contentHash = moduleInfo.contentHash!!,
        )

        val sqlAssetIds = revisionWriter.insertRevisionSqlAssets(
            connection = connection,
            normalizedSchema = normalizedSchema,
            revisionId = newRevisionId,
            configText = moduleInfo.workingCopyYaml,
            sqlFiles = querySupport.readSqlFileContents(moduleInfo.workingCopyJson),
        )
        revisionWriter.insertRevisionStructure(
            connection = connection,
            normalizedSchema = normalizedSchema,
            revisionId = newRevisionId,
            configText = moduleInfo.workingCopyYaml,
            sqlAssetIds = sqlAssetIds,
        )

        lifecycleSupport.updateModuleCurrentRevision(connection, normalizedSchema, moduleInfo.moduleId, newRevisionId)
        lifecycleSupport.deleteWorkingCopyAfterPublish(
            connection = connection,
            normalizedSchema = normalizedSchema,
            moduleId = moduleInfo.moduleId,
            actorId = actorId,
            actorSource = actorSource,
        )

        PublishResult(
            revisionId = newRevisionId,
            revisionNo = newRevisionNo,
            moduleCode = moduleCode,
        )
    }

    fun createModule(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        originKind: String,
        request: RegistryModuleDraft,
    ): RegistryModuleCreationResult = transactionSupport.withTransaction { connection, normalizedSchema ->
        val moduleId = java.util.UUID.randomUUID().toString()
        val revisionId = java.util.UUID.randomUUID().toString()
        val workingCopyId = java.util.UUID.randomUUID().toString()

        lifecycleSupport.insertNewModule(
            connection = connection,
            normalizedSchema = normalizedSchema,
            moduleId = moduleId,
            moduleCode = moduleCode,
            originKind = originKind,
        )

        val revisionSource = if (originKind == "IMPORTED_FROM_FILES") "SYNC_FROM_FILES" else "CREATE_MODULE"
        revisionWriter.insertInitialRevision(
            connection = connection,
            normalizedSchema = normalizedSchema,
            revisionId = revisionId,
            moduleId = moduleId,
            revisionSource = revisionSource,
            request = request,
            actorId = actorId,
        )
        val sqlAssetIds = revisionWriter.insertRevisionSqlAssets(
            connection = connection,
            normalizedSchema = normalizedSchema,
            revisionId = revisionId,
            configText = request.configText,
            sqlFiles = request.sqlFiles,
        )
        revisionWriter.insertRevisionStructure(
            connection = connection,
            normalizedSchema = normalizedSchema,
            revisionId = revisionId,
            configText = request.configText,
            sqlAssetIds = sqlAssetIds,
        )

        lifecycleSupport.updateModuleCurrentRevision(connection, normalizedSchema, moduleId, revisionId)
        lifecycleSupport.insertInitialWorkingCopy(
            connection = connection,
            normalizedSchema = normalizedSchema,
            workingCopyId = workingCopyId,
            moduleId = moduleId,
            revisionId = revisionId,
            actorId = actorId,
            actorSource = actorSource,
            actorDisplayName = actorDisplayName,
            request = request,
        )

        RegistryModuleCreationResult(
            moduleId = moduleId,
            moduleCode = moduleCode,
            revisionId = revisionId,
            workingCopyId = workingCopyId,
        )
    }

    fun deleteModule(
        moduleCode: String,
        actorId: String,
    ): DeleteModuleResult = transactionSupport.withTransaction { connection, normalizedSchema ->
        val moduleInfo = lifecycleSupport.loadModuleForDelete(connection, normalizedSchema, moduleCode)

        require(!moduleInfo.hasActiveRun) {
            "Нельзя удалить модуль с активными запусками. Сначала остановите запуск."
        }

        lifecycleSupport.deleteWorkingCopyForModule(connection, normalizedSchema, moduleInfo.moduleId)
        lifecycleSupport.deleteModuleCascade(connection, normalizedSchema, moduleInfo.moduleId)

        DeleteModuleResult(
            moduleCode = moduleCode,
            moduleId = moduleInfo.moduleId,
            deletedBy = actorId,
        )
    }
}
