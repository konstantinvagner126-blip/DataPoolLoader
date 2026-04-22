package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.sql.ExecutionSnapshotSql
import com.sbrf.lt.datapool.db.registry.sql.ModuleRegistrySql
import com.sbrf.lt.platform.ui.module.DatabaseModuleNotFoundException
import com.sbrf.lt.platform.ui.module.DatabaseModuleSnapshotSupport
import java.sql.Connection

/**
 * Query/load слой для подготовки runtime snapshot DB-модуля.
 */
internal class DatabaseModuleExecutionQuerySupport(
    private val snapshotSupport: DatabaseModuleSnapshotSupport,
) {
    fun loadSource(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): DatabaseExecutionSourceRow {
        connection.prepareStatement(ExecutionSnapshotSql.source(normalizedSchema)).use { stmt ->
            stmt.setString(1, actorId)
            stmt.setString(2, actorSource)
            stmt.setString(3, moduleCode)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) {
                    throw DatabaseModuleNotFoundException(moduleCode)
                }
                return DatabaseExecutionSourceRow(
                    moduleId = rs.getString("module_id"),
                    moduleCode = rs.getString("module_code"),
                    title = rs.getString("title"),
                    configText = rs.getString("config_text"),
                    sourceKind = rs.getString("source_kind"),
                    sourceRevisionId = rs.getString("source_revision_id"),
                    sourceWorkingCopyId = rs.getString("source_working_copy_id"),
                    workingCopyJson = rs.getString("working_copy_json"),
                )
            }
        }
    }

    fun loadSqlFiles(
        connection: Connection,
        normalizedSchema: String,
        source: DatabaseExecutionSourceRow,
    ): Map<String, String> {
        return if (source.sourceKind == "WORKING_COPY") {
            snapshotSupport.deserializeWorkingCopySqlFileContents(source.workingCopyJson)
        } else {
            connection.prepareStatement(ModuleRegistrySql.sqlAssets(normalizedSchema)).use { stmt ->
                stmt.setString(1, source.sourceRevisionId)
                stmt.executeQuery().use { rs ->
                    buildMap {
                        while (rs.next()) {
                            put(rs.getString("asset_key"), rs.getString("sql_text"))
                        }
                    }
                }
            }
        }
    }
}
