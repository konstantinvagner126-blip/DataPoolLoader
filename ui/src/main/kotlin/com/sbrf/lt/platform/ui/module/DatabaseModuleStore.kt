package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sbrf.lt.platform.ui.config.UiModuleStorePostgresConfig
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import com.sbrf.lt.platform.ui.model.ModuleCatalogItemResponse
import com.sbrf.lt.platform.ui.model.ModuleValidationIssueResponse
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.security.MessageDigest
import java.util.UUID

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
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
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

    open fun saveWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        request: SaveModuleRequest,
    ) {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val module = loadModuleForSave(connection, normalizedSchema, moduleCode, actorId, actorSource)
                upsertWorkingCopy(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    module = module,
                    actorId = actorId,
                    actorSource = actorSource,
                    actorDisplayName = actorDisplayName,
                    request = request,
                )
                connection.commit()
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    open fun discardWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ) {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(discardWorkingCopySql(normalizedSchema)).use { statement ->
                statement.setString(1, actorId)
                statement.setString(2, actorSource)
                statement.setString(3, moduleCode)
                statement.executeUpdate()
            }
        }
    }

    open fun publishWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): PublishResult {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val moduleInfo = loadModuleForPublish(connection, normalizedSchema, moduleCode, actorId, actorSource)
                require(moduleInfo.hasWorkingCopy) {
                    "Нет working copy для публикации. Сначала сохраните изменения."
                }

                val newRevisionId = UUID.randomUUID().toString()
                val newRevisionNo = moduleInfo.maxRevisionNo + 1

                insertNewRevision(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    revisionId = newRevisionId,
                    moduleId = moduleInfo.moduleId,
                    revisionNo = newRevisionNo,
                    actorId = actorId,
                    actorDisplayName = actorDisplayName,
                    workingCopyJson = moduleInfo.workingCopyJson!!,
                    workingCopyYaml = moduleInfo.workingCopyYaml!!,
                    contentHash = moduleInfo.contentHash!!,
                )

                copySqlAssetsFromWorkingCopy(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    newRevisionId = newRevisionId,
                    workingCopyJson = moduleInfo.workingCopyJson,
                )

                updateModuleCurrentRevision(connection, normalizedSchema, moduleInfo.moduleId, newRevisionId)

                deleteWorkingCopyAfterPublish(connection, normalizedSchema, moduleInfo.moduleId, actorId, actorSource)

                connection.commit()
                return PublishResult(
                    revisionId = newRevisionId,
                    revisionNo = newRevisionNo,
                    moduleCode = moduleCode,
                )
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    open fun createModule(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        request: CreateModuleRequest,
    ): CreateModuleResult {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val moduleId = UUID.randomUUID().toString()
                val revisionId = UUID.randomUUID().toString()
                val workingCopyId = UUID.randomUUID().toString()

                insertNewModule(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    moduleId = moduleId,
                    moduleCode = moduleCode,
                    originKind = "CREATED_IN_UI",
                )

                insertInitialRevision(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    revisionId = revisionId,
                    moduleId = moduleId,
                    request = request,
                    actorId = actorId,
                )

                updateModuleCurrentRevision(connection, normalizedSchema, moduleId, revisionId)

                insertInitialWorkingCopy(
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

                connection.commit()
                return CreateModuleResult(
                    moduleId = moduleId,
                    moduleCode = moduleCode,
                    revisionId = revisionId,
                    workingCopyId = workingCopyId,
                )
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    open fun deleteModule(
        moduleCode: String,
        actorId: String,
    ): DeleteModuleResult {
        val normalizedSchema = normalizeSchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val moduleInfo = loadModuleForDelete(connection, normalizedSchema, moduleCode)

                require(!moduleInfo.hasActiveRun) {
                    "Нельзя удалить модуль с активными запусками. Сначала остановите запуск."
                }

                deleteWorkingCopyForModule(connection, normalizedSchema, moduleInfo.moduleId)

                deleteModuleCascade(connection, normalizedSchema, moduleInfo.moduleId)

                connection.commit()
                return DeleteModuleResult(
                    moduleCode = moduleCode,
                    moduleId = moduleInfo.moduleId,
                    deletedBy = actorId,
                )
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    private fun loadModuleForSave(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): DatabaseModuleForSave {
        connection.prepareStatement(moduleForSaveSql(normalizedSchema)).use { statement ->
            statement.setString(1, actorId)
            statement.setString(2, actorSource)
            statement.setString(3, moduleCode)
            statement.executeQuery().use { resultSet ->
                if (!resultSet.next()) {
                    error("DB-модуль '$moduleCode' не найден.")
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

    private fun upsertWorkingCopy(
        connection: Connection,
        normalizedSchema: String,
        module: DatabaseModuleForSave,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        request: SaveModuleRequest,
    ) {
        val snapshotJson = buildWorkingCopyJson(request)
        connection.prepareStatement(upsertWorkingCopySql(normalizedSchema)).use { statement ->
            statement.setString(1, module.workingCopyId ?: UUID.randomUUID().toString())
            statement.setString(2, module.moduleId)
            statement.setString(3, actorId)
            statement.setString(4, actorSource)
            statement.setString(5, actorDisplayName)
            statement.setString(6, module.currentRevisionId)
            statement.setString(7, snapshotJson)
            statement.setString(8, request.configText)
            statement.setString(9, contentHash(request))
            statement.executeUpdate()
        }
    }

    private fun buildWorkingCopyJson(request: SaveModuleRequest): String {
        val sqlFiles = request.sqlFiles.entries
            .sortedBy { it.key }
            .map { (path, content) ->
                ModuleFileContent(
                    label = path,
                    path = path,
                    content = content,
                    exists = true,
                )
            }
        val root = objectMapper.createObjectNode()
        root.put("configText", request.configText)
        root.set<com.fasterxml.jackson.databind.JsonNode>("sqlFiles", objectMapper.valueToTree(sqlFiles))
        return objectMapper.writeValueAsString(root)
    }

    private fun contentHash(request: SaveModuleRequest): String {
        val input = buildString {
            append(request.configText)
            request.sqlFiles.toSortedMap().forEach { (path, content) ->
                append('\n')
                append(path)
                append('\u0000')
                append(content)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
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

    private fun loadModuleForPublish(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): ModuleForPublish {
        connection.prepareStatement(moduleForPublishSql(normalizedSchema)).use { stmt ->
            stmt.setString(1, actorId)
            stmt.setString(2, actorSource)
            stmt.setString(3, moduleCode)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) error("DB-модуль '$moduleCode' не найден.")
                return ModuleForPublish(
                    moduleId = rs.getString("module_id"),
                    currentRevisionId = rs.getString("current_revision_id"),
                    maxRevisionNo = rs.getLong("max_revision_no"),
                    hasWorkingCopy = rs.getString("working_copy_id") != null,
                    workingCopyJson = rs.getString("working_copy_json"),
                    workingCopyYaml = rs.getString("working_copy_yaml"),
                    contentHash = rs.getString("content_hash"),
                )
            }
        }
    }

    private fun insertNewRevision(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
        moduleId: String,
        revisionNo: Long,
        actorId: String,
        actorDisplayName: String?,
        workingCopyJson: String,
        workingCopyYaml: String,
        contentHash: String,
    ) {
        val wcJson = objectMapper.readTree(workingCopyJson)
        val configText = wcJson.path("configText").asText("")
        val config = try {
            com.sbrf.lt.datapool.config.ConfigLoader().objectMapper().readValue(configText, com.sbrf.lt.datapool.model.RootConfig::class.java)
        } catch (e: Exception) {
            com.sbrf.lt.datapool.model.RootConfig(app = com.sbrf.lt.datapool.model.AppConfig())
        }

        val title = config.app.title ?: "DB Module"
        val description = config.app.description
        val tagsArray = objectMapper.createArrayNode()
        config.app.tags?.forEach { tagsArray.add(it) }

        connection.prepareStatement(insertRevisionSql(normalizedSchema)).use { stmt ->
            stmt.setString(1, revisionId)
            stmt.setString(2, moduleId)
            stmt.setLong(3, revisionNo)
            stmt.setString(4, actorDisplayName ?: actorId)
            stmt.setString(5, title)
            stmt.setString(6, description)
            stmt.setString(7, tagsArray.toString())
            stmt.setString(8, workingCopyJson)
            stmt.setString(9, workingCopyYaml)
            stmt.setString(10, contentHash)
            stmt.executeUpdate()
        }
    }

    private fun copySqlAssetsFromWorkingCopy(
        connection: Connection,
        normalizedSchema: String,
        newRevisionId: String,
        workingCopyJson: String,
    ) {
        val wcJson = objectMapper.readTree(workingCopyJson)
        val sqlFilesNode = wcJson.path("sqlFiles")
        if (!sqlFilesNode.isArray) return

        var sortOrder = 0
        sqlFilesNode.forEach { fileNode ->
            val assetId = UUID.randomUUID().toString()
            val assetKey = fileNode.path("path").asText("")
            val label = fileNode.path("label").asText("")
            val sqlText = fileNode.path("content").asText("")
            val contentHash = MessageDigest.getInstance("SHA-256")
                .digest(sqlText.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

            connection.prepareStatement(copySqlAssetsSql(normalizedSchema)).use { stmt ->
                stmt.setString(1, assetId)
                stmt.setString(2, newRevisionId)
                stmt.setString(3, if (assetKey.contains("common", ignoreCase = true)) "COMMON" else "SOURCE")
                stmt.setString(4, assetKey)
                stmt.setString(5, label)
                stmt.setString(6, sqlText)
                stmt.setInt(7, sortOrder)
                stmt.setString(8, contentHash)
                stmt.executeUpdate()
            }
            sortOrder++
        }
    }

    private fun updateModuleCurrentRevision(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
        revisionId: String,
    ) {
        connection.prepareStatement(updateCurrentRevisionSql(normalizedSchema)).use { stmt ->
            stmt.setString(1, revisionId)
            stmt.setString(2, moduleId)
            stmt.executeUpdate()
        }
    }

    private fun deleteWorkingCopyAfterPublish(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
        actorId: String,
        actorSource: String,
    ) {
        connection.prepareStatement(deleteWorkingCopyAfterPublishSql(normalizedSchema)).use { stmt ->
            stmt.setString(1, actorId)
            stmt.setString(2, actorSource)
            stmt.setString(3, moduleId)
            stmt.executeUpdate()
        }
    }

    private fun loadModuleForDelete(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
    ): ModuleForDelete {
        val moduleId: String
        connection.prepareStatement("select module_id::text as module_id from $normalizedSchema.module where module_code = ?").use { stmt ->
            stmt.setString(1, moduleCode)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) error("DB-модуль '$moduleCode' не найден.")
                moduleId = rs.getString("module_id")
            }
        }

        var hasActiveRun = false
        connection.prepareStatement(checkActiveRunSql(normalizedSchema)).use { stmt ->
            stmt.setString(1, moduleCode)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    hasActiveRun = rs.getInt("active_runs") > 0
                }
            }
        }

        return ModuleForDelete(moduleId = moduleId, hasActiveRun = hasActiveRun)
    }

    private fun insertNewModule(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
        moduleCode: String,
        originKind: String,
    ) {
        connection.prepareStatement(insertModuleSql(normalizedSchema)).use { stmt ->
            stmt.setString(1, moduleId)
            stmt.setString(2, moduleCode)
            stmt.setString(3, originKind)
            stmt.executeUpdate()
        }
    }

    private fun insertInitialRevision(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
        moduleId: String,
        request: CreateModuleRequest,
        actorId: String,
    ) {
        val tagsArray = objectMapper.createArrayNode()
        request.tags?.forEach { tagsArray.add(it) }

        val snapshotJson = objectMapper.createObjectNode()
        snapshotJson.put("configText", request.configText)
        snapshotJson.set<com.fasterxml.jackson.databind.JsonNode>("sqlFiles", objectMapper.createArrayNode())

        connection.prepareStatement(insertRevisionSql(normalizedSchema)).use { stmt ->
            stmt.setString(1, revisionId)
            stmt.setString(2, moduleId)
            stmt.setLong(3, 1)
            stmt.setString(4, actorId)
            stmt.setString(5, request.title)
            stmt.setString(6, request.description)
            stmt.setString(7, tagsArray.toString())
            stmt.setString(8, snapshotJson.toString())
            stmt.setString(9, request.configText)
            stmt.setString(10, contentHashForCreate(request))
            stmt.executeUpdate()
        }
    }

    private fun insertInitialWorkingCopy(
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
        val snapshotJson = objectMapper.createObjectNode()
        snapshotJson.put("configText", request.configText)
        snapshotJson.set<com.fasterxml.jackson.databind.JsonNode>("sqlFiles", objectMapper.createArrayNode())

        connection.prepareStatement(upsertWorkingCopySql(normalizedSchema)).use { stmt ->
            stmt.setString(1, workingCopyId)
            stmt.setString(2, moduleId)
            stmt.setString(3, actorId)
            stmt.setString(4, actorSource)
            stmt.setString(5, actorDisplayName)
            stmt.setString(6, revisionId)
            stmt.setString(7, snapshotJson.toString())
            stmt.setString(8, request.configText)
            stmt.setString(9, contentHashForCreate(request))
            stmt.executeUpdate()
        }
    }

    private fun contentHashForCreate(request: CreateModuleRequest): String {
        val input = request.configText
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun deleteWorkingCopyForModule(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
    ) {
        connection.prepareStatement(deleteWorkingCopyForModuleSql(normalizedSchema)).use { stmt ->
            stmt.setString(1, moduleId)
            stmt.executeUpdate()
        }
    }

    private fun deleteModuleCascade(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
    ) {
        connection.prepareStatement(deleteModuleSql(normalizedSchema)).use { stmt ->
            stmt.setString(1, moduleId)
            stmt.executeUpdate()
        }
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

        private fun moduleForSaveSql(schema: String): String =
            """
            select
                m.module_id::text as module_id,
                m.current_revision_id::text as current_revision_id,
                w.working_copy_id::text as working_copy_id,
                w.status as working_copy_status
            from $schema.module m
            left join $schema.module_working_copy w
                on w.module_id = m.module_id
                and w.owner_actor_id = ?
                and w.owner_actor_source = ?
            where m.module_code = ?
            """.trimIndent()

        private fun upsertWorkingCopySql(schema: String): String =
            """
            insert into $schema.module_working_copy (
                working_copy_id,
                module_id,
                owner_actor_id,
                owner_actor_source,
                owner_actor_display_name,
                base_revision_id,
                status,
                working_copy_json,
                working_copy_yaml,
                content_hash
            ) values (
                ?::uuid,
                ?::uuid,
                ?,
                ?,
                ?,
                ?::uuid,
                'DIRTY',
                ?::jsonb,
                ?,
                ?
            )
            on conflict (module_id, owner_actor_id, owner_actor_source)
            do update set
                owner_actor_display_name = excluded.owner_actor_display_name,
                status = case
                    when $schema.module_working_copy.status = 'STALE' then 'STALE'
                    else 'DIRTY'
                end,
                working_copy_json = excluded.working_copy_json,
                working_copy_yaml = excluded.working_copy_yaml,
                content_hash = excluded.content_hash,
                updated_at = now()
            """.trimIndent()

        private fun discardWorkingCopySql(schema: String): String =
            """
            delete from $schema.module_working_copy w
            using $schema.module m
            where w.module_id = m.module_id
                and w.owner_actor_id = ?
                and w.owner_actor_source = ?
                and m.module_code = ?
            """.trimIndent()

        private fun moduleForPublishSql(schema: String): String =
            """
            select
                m.module_id::text as module_id,
                m.current_revision_id::text as current_revision_id,
                r.revision_no as max_revision_no,
                w.working_copy_id::text as working_copy_id,
                w.working_copy_json::text as working_copy_json,
                w.working_copy_yaml as working_copy_yaml,
                w.content_hash as content_hash
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

        private fun insertRevisionSql(schema: String): String =
            """
            insert into $schema.module_revision (
                revision_id,
                module_id,
                revision_no,
                created_by,
                revision_source,
                title,
                description,
                tags,
                hidden_from_ui,
                validation_status,
                validation_issues,
                output_dir,
                file_format,
                merge_mode,
                error_mode,
                parallelism,
                fetch_size,
                query_timeout_sec,
                progress_log_every_rows,
                max_merged_rows,
                delete_output_files_after_completion,
                snapshot_json,
                snapshot_yaml,
                content_hash
            ) values (
                ?::uuid,
                ?::uuid,
                ?,
                ?,
                'PUBLISH',
                ?,
                ?,
                ?::jsonb,
                false,
                'VALID',
                '[]'::jsonb,
                'output',
                'CSV',
                'PLAIN',
                'CONTINUE_ON_ERROR',
                4,
                1000,
                null,
                10000,
                null,
                false,
                ?::jsonb,
                ?,
                ?
            )
            """.trimIndent()

        private fun copySqlAssetsSql(schema: String): String =
            """
            insert into $schema.module_revision_sql_asset (
                sql_asset_id,
                revision_id,
                asset_kind,
                asset_key,
                label,
                sql_text,
                origin_kind,
                origin_path,
                sort_order,
                content_hash
            ) values (
                ?::uuid,
                ?::uuid,
                ?,
                ?,
                ?,
                ?,
                'INLINE',
                null,
                ?,
                ?
            )
            """.trimIndent()

        private fun updateCurrentRevisionSql(schema: String): String =
            """
            update $schema.module
            set current_revision_id = ?::uuid,
                updated_at = now()
            where module_id = ?::uuid
            """.trimIndent()

        private fun deleteWorkingCopyAfterPublishSql(schema: String): String =
            """
            delete from $schema.module_working_copy w
            using $schema.module m
            where w.module_id = m.module_id
                and w.owner_actor_id = ?
                and w.owner_actor_source = ?
                and m.module_id = ?::uuid
            """.trimIndent()

        private fun insertModuleSql(schema: String): String =
            """
            insert into $schema.module (
                module_id,
                module_code,
                module_origin_kind,
                current_revision_id
            ) values (
                ?::uuid,
                ?,
                ?,
                null
            )
            """.trimIndent()

        private fun deleteWorkingCopyForModuleSql(schema: String): String =
            """
            delete from $schema.module_working_copy
            where module_id = ?::uuid
            """.trimIndent()

        private fun deleteModuleSql(schema: String): String =
            """
            delete from $schema.module
            where module_id = ?::uuid
            """.trimIndent()

        private fun checkActiveRunSql(schema: String): String =
            """
            select count(*) as active_runs
            from $schema.module_run mr
            join $schema.module m on m.module_id = mr.module_id
            where m.module_code = ?
                and mr.status = 'RUNNING'
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

private data class DatabaseModuleForSave(
    val moduleId: String,
    val currentRevisionId: String,
    val workingCopyId: String?,
    val workingCopyStatus: String?,
)

private data class ModuleForPublish(
    val moduleId: String,
    val currentRevisionId: String,
    val maxRevisionNo: Long,
    val hasWorkingCopy: Boolean,
    val workingCopyJson: String?,
    val workingCopyYaml: String?,
    val contentHash: String?,
)

private data class ModuleForDelete(
    val moduleId: String,
    val hasActiveRun: Boolean,
)

data class PublishResult(
    val revisionId: String,
    val revisionNo: Long,
    val moduleCode: String,
)

data class CreateModuleRequest(
    val title: String,
    val description: String? = null,
    val tags: List<String>? = null,
    val configText: String,
    val hiddenFromUi: Boolean = true,
)

data class CreateModuleResult(
    val moduleId: String,
    val moduleCode: String,
    val revisionId: String,
    val workingCopyId: String,
)

data class DeleteModuleResult(
    val moduleCode: String,
    val moduleId: String,
    val deletedBy: String,
)
