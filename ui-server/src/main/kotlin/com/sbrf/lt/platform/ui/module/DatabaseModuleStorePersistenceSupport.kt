package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.db.registry.model.RegistryModuleDraft
import com.sbrf.lt.datapool.db.registry.sql.ModuleRegistrySql
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import java.sql.Connection
import java.util.UUID

/**
 * Persistence/write слой DB module mutation lifecycle.
 */
internal class DatabaseModuleStorePersistenceSupport(
    private val snapshotSupport: DatabaseModuleSnapshotSupport,
) {
    fun upsertWorkingCopy(
        connection: Connection,
        normalizedSchema: String,
        module: DatabaseModuleForSave,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        request: SaveModuleRequest,
    ) {
        val snapshotJson = snapshotSupport.serializeWorkingCopySnapshot(
            configText = request.configText,
            sqlFileContents = request.sqlFiles,
            title = request.title,
            description = request.description,
            tags = request.tags,
            hiddenFromUi = request.hiddenFromUi,
        )
        connection.prepareStatement(ModuleRegistrySql.upsertWorkingCopy(normalizedSchema)).use { statement ->
            statement.setString(1, module.workingCopyId ?: UUID.randomUUID().toString())
            statement.setString(2, module.moduleId)
            statement.setString(3, actorId)
            statement.setString(4, actorSource)
            statement.setString(5, actorDisplayName)
            statement.setString(6, module.currentRevisionId)
            statement.setString(7, snapshotJson)
            statement.setString(8, request.configText)
            statement.setString(
                9,
                snapshotSupport.calculateRevisionContentHash(
                    configText = request.configText,
                    title = request.title,
                    description = request.description,
                    tags = request.tags,
                    hiddenFromUi = request.hiddenFromUi,
                    sqlFiles = request.sqlFiles,
                ),
            )
            statement.executeUpdate()
        }
    }

    fun updateModuleCurrentRevision(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
        revisionId: String,
    ) {
        connection.prepareStatement(ModuleRegistrySql.updateCurrentRevision(normalizedSchema)).use { stmt ->
            stmt.setString(1, revisionId)
            stmt.setString(2, moduleId)
            stmt.executeUpdate()
        }
    }

    fun deleteWorkingCopyAfterPublish(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
        actorId: String,
        actorSource: String,
    ) {
        connection.prepareStatement(ModuleRegistrySql.deleteWorkingCopyAfterPublish(normalizedSchema)).use { stmt ->
            stmt.setString(1, actorId)
            stmt.setString(2, actorSource)
            stmt.setString(3, moduleId)
            stmt.executeUpdate()
        }
    }

    fun insertNewModule(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
        moduleCode: String,
        originKind: String,
    ) {
        connection.prepareStatement(ModuleRegistrySql.insertModule(normalizedSchema)).use { stmt ->
            stmt.setString(1, moduleId)
            stmt.setString(2, moduleCode)
            stmt.setString(3, originKind)
            stmt.executeUpdate()
        }
    }

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
    ) {
        val snapshotJson = snapshotSupport.serializeWorkingCopySnapshot(
            configText = request.configText,
            sqlFileContents = request.sqlFiles,
            title = request.title,
            description = request.description,
            tags = request.tags,
            hiddenFromUi = request.hiddenFromUi,
        )

        connection.prepareStatement(ModuleRegistrySql.upsertWorkingCopy(normalizedSchema)).use { stmt ->
            stmt.setString(1, workingCopyId)
            stmt.setString(2, moduleId)
            stmt.setString(3, actorId)
            stmt.setString(4, actorSource)
            stmt.setString(5, actorDisplayName)
            stmt.setString(6, revisionId)
            stmt.setString(7, snapshotJson)
            stmt.setString(8, request.configText)
            stmt.setString(
                9,
                snapshotSupport.calculateRevisionContentHash(
                    configText = request.configText,
                    title = request.title,
                    description = request.description,
                    tags = request.tags,
                    hiddenFromUi = request.hiddenFromUi,
                    sqlFiles = request.sqlFiles,
                ),
            )
            stmt.executeUpdate()
        }
    }

    fun deleteWorkingCopyForModule(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
    ) {
        connection.prepareStatement(ModuleRegistrySql.deleteWorkingCopyForModule(normalizedSchema)).use { stmt ->
            stmt.setString(1, moduleId)
            stmt.executeUpdate()
        }
    }

    fun deleteModuleCascade(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
    ) {
        connection.prepareStatement(ModuleRegistrySql.deleteModule(normalizedSchema)).use { stmt ->
            stmt.setString(1, moduleId)
            stmt.executeUpdate()
        }
    }
}
