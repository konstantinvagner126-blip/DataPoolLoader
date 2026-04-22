package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.db.registry.sql.ModuleRegistrySql
import java.sql.Connection

/**
 * Lookup/query слой DB module mutation lifecycle.
 */
internal class DatabaseModuleStoreLookupSupport {
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
}
