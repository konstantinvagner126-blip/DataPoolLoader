package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.platform.ui.config.UiModuleStorePostgresConfig
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import com.sbrf.lt.platform.ui.model.ModuleCatalogItemResponse
import com.sbrf.lt.platform.ui.model.ModuleValidationIssueResponse
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

fun interface DatabaseConnectionProvider {
    fun getConnection(): Connection
}

class DriverManagerDatabaseConnectionProvider(
    private val config: UiModuleStorePostgresConfig,
) : DatabaseConnectionProvider {
    override fun getConnection(): Connection =
        DriverManager.getConnection(config.jdbcUrl, config.username, config.password)
}

open class DatabaseModuleStore(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String = UiModuleStorePostgresConfig.DEFAULT_SCHEMA,
    private val objectMapper: ObjectMapper = ConfigLoader().objectMapper(),
) {
    private val tagsType = object : TypeReference<List<String>>() {}
    private val issuesType = object : TypeReference<List<ModuleValidationIssueResponse>>() {}
    private val sqlFilesType = object : TypeReference<List<ModuleFileContent>>() {}

    open fun listModules(includeHidden: Boolean = false): List<ModuleCatalogItemResponse> {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(catalogSql(normalizedSchema)).use { statement ->
                statement.setBoolean(1, includeHidden)
                statement.executeQuery().use { resultSet ->
                    val modules = mutableListOf<ModuleCatalogItemResponse>()
                    while (resultSet.next()) {
                        modules += resultSet.toCatalogItem()
                    }
                    return modules
                }
            }
        }
    }

    open fun loadModuleDetails(
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): DatabaseEditableModule {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val row = connection.prepareStatement(detailsSql(normalizedSchema)).use { statement ->
                statement.setString(1, actorId)
                statement.setString(2, actorSource)
                statement.setString(3, moduleCode)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        error("DB-модуль '$moduleCode' не найден.")
                    }
                    resultSet.toEditableModuleRow()
                }
            }
            val sqlFiles = if (row.sourceKind == "WORKING_COPY") {
                row.workingCopyJson?.let { readWorkingCopySqlFiles(it) } ?: emptyList()
            } else {
                loadRevisionSqlAssets(connection, normalizedSchema, row.currentRevisionId)
            }

            return DatabaseEditableModule(
                module = ModuleDetailsResponse(
                    id = row.moduleCode,
                    title = row.title,
                    description = row.description,
                    tags = row.tags,
                    validationStatus = row.validationStatus,
                    validationIssues = row.validationIssues,
                    configPath = "db:${row.moduleCode}",
                    configText = row.configText,
                    sqlFiles = sqlFiles,
                    requiresCredentials = false,
                    credentialsStatus = CredentialsStatusResponse(
                        mode = "NONE",
                        displayName = "Файл не задан",
                        fileAvailable = false,
                        uploaded = false,
                    ),
                ),
                sourceKind = row.sourceKind,
                currentRevisionId = row.currentRevisionId,
                workingCopyId = row.workingCopyId,
                workingCopyStatus = row.workingCopyStatus,
                baseRevisionId = row.baseRevisionId,
            )
        }
    }

    private fun loadRevisionSqlAssets(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
    ): List<ModuleFileContent> {
        connection.prepareStatement(sqlAssetsSql(normalizedSchema)).use { statement ->
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

    private fun readWorkingCopySqlFiles(workingCopyJson: String): List<ModuleFileContent> {
        val root = objectMapper.readTree(workingCopyJson)
        val sqlFilesNode = root.path("sqlFiles").takeIf { it.isArray } ?: return emptyList()
        return objectMapper.readValue(sqlFilesNode.traverse(objectMapper), sqlFilesType)
    }

    private fun ResultSet.toCatalogItem(): ModuleCatalogItemResponse =
        ModuleCatalogItemResponse(
            id = getString("module_code"),
            title = getString("title"),
            description = getString("description"),
            tags = readJsonList(getString("tags_json"), tagsType),
            validationStatus = getString("validation_status"),
            validationIssues = readJsonList(getString("validation_issues_json"), issuesType),
        )

    private fun ResultSet.toEditableModuleRow(): DatabaseEditableModuleRow =
        DatabaseEditableModuleRow(
            moduleCode = getString("module_code"),
            title = getString("title"),
            description = getString("description"),
            tags = readJsonList(getString("tags_json"), tagsType),
            validationStatus = getString("validation_status"),
            validationIssues = readJsonList(getString("validation_issues_json"), issuesType),
            configText = getString("config_text"),
            sourceKind = getString("source_kind"),
            currentRevisionId = getString("current_revision_id"),
            workingCopyId = getString("working_copy_id"),
            workingCopyStatus = getString("working_copy_status"),
            baseRevisionId = getString("base_revision_id"),
            workingCopyJson = getString("working_copy_json"),
        )

    private fun <T> readJsonList(json: String?, type: TypeReference<List<T>>): List<T> {
        if (json.isNullOrBlank()) {
            return emptyList()
        }
        return objectMapper.readValue(json, type)
    }

    companion object {
        fun fromConfig(config: UiModuleStorePostgresConfig): DatabaseModuleStore =
            DatabaseModuleStore(
                connectionProvider = DriverManagerDatabaseConnectionProvider(config),
                schema = config.schemaName(),
            )

        private fun normalizeSchemaName(schema: String): String {
            val normalized = schema.trim()
            require(Regex("[A-Za-z_][A-Za-z0-9_]*").matches(normalized)) {
                "Некорректное имя schema PostgreSQL registry: $schema"
            }
            return normalized
        }

        private fun catalogSql(schema: String): String =
            """
            select
                m.module_code as module_code,
                r.title as title,
                r.description as description,
                r.tags::text as tags_json,
                r.validation_status as validation_status,
                r.validation_issues::text as validation_issues_json
            from $schema.module m
            join $schema.module_revision r
                on r.module_id = m.module_id
                and r.revision_id = m.current_revision_id
            where (? = true or r.hidden_from_ui = false)
            order by m.module_code
            """.trimIndent()

        private fun detailsSql(schema: String): String =
            """
            select
                m.module_code as module_code,
                r.revision_id::text as current_revision_id,
                r.title as title,
                r.description as description,
                r.tags::text as tags_json,
                r.validation_status as validation_status,
                r.validation_issues::text as validation_issues_json,
                coalesce(w.working_copy_yaml, r.snapshot_yaml) as config_text,
                case
                    when w.working_copy_id is null then 'CURRENT_REVISION'
                    else 'WORKING_COPY'
                end as source_kind,
                w.working_copy_id::text as working_copy_id,
                w.status as working_copy_status,
                w.base_revision_id::text as base_revision_id,
                w.working_copy_json::text as working_copy_json
            from $schema.module m
            join $schema.module_revision r
                on r.module_id = m.module_id
                and r.revision_id = m.current_revision_id
            left join $schema.module_working_copy w
                on w.module_id = m.module_id
                and w.owner_actor_id = ?
                and w.owner_actor_source = ?
            where m.module_code = ?
            """.trimIndent()

        private fun sqlAssetsSql(schema: String): String =
            """
            select
                label,
                asset_key,
                sql_text
            from $schema.module_revision_sql_asset
            where revision_id = ?::uuid
            order by sort_order, label
            """.trimIndent()
    }
}

data class DatabaseEditableModule(
    val module: ModuleDetailsResponse,
    val sourceKind: String,
    val currentRevisionId: String,
    val workingCopyId: String? = null,
    val workingCopyStatus: String? = null,
    val baseRevisionId: String? = null,
)

private data class DatabaseEditableModuleRow(
    val moduleCode: String,
    val title: String,
    val description: String?,
    val tags: List<String>,
    val validationStatus: String,
    val validationIssues: List<ModuleValidationIssueResponse>,
    val configText: String,
    val sourceKind: String,
    val currentRevisionId: String,
    val workingCopyId: String?,
    val workingCopyStatus: String?,
    val baseRevisionId: String?,
    val workingCopyJson: String?,
)
