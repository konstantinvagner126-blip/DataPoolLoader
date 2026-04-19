package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.db.registry.sql.ModuleRegistrySql
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import java.sql.Connection
import java.util.UUID

internal class DatabaseModuleStoreLifecycleSupport(
    private val support: DatabaseModuleStoreSupport,
    private val revisionWriter: DatabaseModuleRevisionWriter,
) {
    fun loadModuleForSave(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): DatabaseModuleForSave {
        connection.prepareStatement(ModuleRegistrySql.moduleForSave(normalizedSchema)).use { statement ->
            statement.setString(1, actorId)
            statement.setString(2, actorSource)
            statement.setString(3, moduleCode)
            statement.executeQuery().use { resultSet ->
                if (!resultSet.next()) {
                    throw DatabaseModuleNotFoundException(moduleCode)
                }
                return DatabaseModuleForSave(
                    moduleId = resultSet.getString("module_id"),
                    currentRevisionId = resultSet.getString("current_revision_id"),
                    workingCopyId = resultSet.getString("working_copy_id"),
                    workingCopyStatus = resultSet.getString("working_copy_status"),
                )
            }
        }
    }

    fun upsertWorkingCopy(
        connection: Connection,
        normalizedSchema: String,
        module: DatabaseModuleForSave,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        request: SaveModuleRequest,
    ) {
        val snapshotJson = support.buildWorkingCopyJson(request)
        connection.prepareStatement(ModuleRegistrySql.upsertWorkingCopy(normalizedSchema)).use { statement ->
            statement.setString(1, module.workingCopyId ?: UUID.randomUUID().toString())
            statement.setString(2, module.moduleId)
            statement.setString(3, actorId)
            statement.setString(4, actorSource)
            statement.setString(5, actorDisplayName)
            statement.setString(6, module.currentRevisionId)
            statement.setString(7, snapshotJson)
            statement.setString(8, request.configText)
            statement.setString(9, support.contentHash(request))
            statement.executeUpdate()
        }
    }

    fun loadModuleForPublish(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): ModuleForPublish {
        connection.prepareStatement(ModuleRegistrySql.moduleForPublish(normalizedSchema)).use { stmt ->
            stmt.setString(1, actorId)
            stmt.setString(2, actorSource)
            stmt.setString(3, moduleCode)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) throw DatabaseModuleNotFoundException(moduleCode)
                return ModuleForPublish(
                    moduleId = rs.getString("module_id"),
                    currentRevisionId = rs.getString("current_revision_id"),
                    maxRevisionNo = rs.getLong("max_revision_no"),
                    hasWorkingCopy = rs.getString("working_copy_id") != null,
                    baseRevisionId = rs.getString("base_revision_id"),
                    workingCopyStatus = rs.getString("working_copy_status"),
                    workingCopyJson = rs.getString("working_copy_json"),
                    workingCopyYaml = rs.getString("working_copy_yaml"),
                    contentHash = rs.getString("content_hash"),
                )
            }
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

    fun loadModuleForDelete(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
    ): ModuleForDelete {
        val moduleId: String
        connection.prepareStatement(
            "select module_id::text as module_id from $normalizedSchema.module where module_code = ?",
        ).use { stmt ->
            stmt.setString(1, moduleCode)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) throw DatabaseModuleNotFoundException(moduleCode)
                moduleId = rs.getString("module_id")
            }
        }

        var hasActiveRun = false
        connection.prepareStatement(ModuleRegistrySql.checkActiveRun(normalizedSchema)).use { stmt ->
            stmt.setString(1, moduleCode)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    hasActiveRun = rs.getInt("active_runs") > 0
                }
            }
        }

        return ModuleForDelete(moduleId = moduleId, hasActiveRun = hasActiveRun)
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
        request: CreateModuleRequest,
    ) {
        val snapshotJson = support.buildSnapshotJson(
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
            stmt.setString(9, revisionWriter.contentHashForCreate(request))
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
