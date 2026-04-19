package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.ModuleRegistrySql
import com.sbrf.lt.datapool.db.registry.sql.normalizeRegistrySchemaName
import com.sbrf.lt.platform.ui.model.ModuleCatalogItemResponse
import com.sbrf.lt.platform.ui.model.ModuleFileContent

internal class DatabaseModuleStoreQuerySupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String,
    private val support: DatabaseModuleStoreSupport,
) {
    fun listModules(includeHidden: Boolean): List<ModuleCatalogItemResponse> {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(ModuleRegistrySql.catalog(normalizedSchema)).use { statement ->
                statement.setBoolean(1, includeHidden)
                statement.executeQuery().use { resultSet ->
                    val modules = mutableListOf<ModuleCatalogItemResponse>()
                    while (resultSet.next()) {
                        val configText = resultSet.getString("snapshot_yaml")
                        val sqlFiles = loadRevisionSqlAssets(
                            connection = connection,
                            normalizedSchema = normalizedSchema,
                            revisionId = resultSet.getString("current_revision_id"),
                        )
                        modules += support.catalogItem(resultSet, configText, sqlFiles)
                    }
                    return modules
                }
            }
        }
    }

    fun loadModuleDetails(
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): DatabaseEditableModule {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val row = connection.prepareStatement(ModuleRegistrySql.details(normalizedSchema)).use { statement ->
                statement.setString(1, actorId)
                statement.setString(2, actorSource)
                statement.setString(3, moduleCode)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        error("DB-модуль '$moduleCode' не найден.")
                    }
                    support.editableModuleRow(resultSet)
                }
            }
            val sqlFiles = if (row.sourceKind == "WORKING_COPY") {
                row.workingCopyJson?.let(support::readWorkingCopySqlFiles) ?: emptyList()
            } else {
                loadRevisionSqlAssets(connection, normalizedSchema, row.currentRevisionId)
            }

            return DatabaseEditableModule(
                module = support.moduleDetails(row, sqlFiles),
                sourceKind = row.sourceKind,
                currentRevisionId = row.currentRevisionId,
                workingCopyId = row.workingCopyId,
                workingCopyStatus = row.workingCopyStatus,
                baseRevisionId = row.baseRevisionId,
            )
        }
    }

    fun readSqlFileContents(workingCopyJson: String?): Map<String, String> =
        support.readSqlFileContents(workingCopyJson)

    private fun loadRevisionSqlAssets(
        connection: java.sql.Connection,
        normalizedSchema: String,
        revisionId: String,
    ): List<ModuleFileContent> {
        connection.prepareStatement(ModuleRegistrySql.sqlAssets(normalizedSchema)).use { statement ->
            statement.setString(1, revisionId)
            statement.executeQuery().use { resultSet ->
                val sqlFiles = mutableListOf<ModuleFileContent>()
                while (resultSet.next()) {
                    sqlFiles += ModuleFileContent(
                        label = resultSet.getString("label"),
                        path = resultSet.getString("asset_key"),
                        content = resultSet.getString("sql_text"),
                        exists = true,
                    )
                }
                return sqlFiles
            }
        }
    }
}
