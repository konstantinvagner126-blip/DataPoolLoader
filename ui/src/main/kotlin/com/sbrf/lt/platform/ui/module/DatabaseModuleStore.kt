package com.sbrf.lt.platform.ui.module

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sbrf.lt.datapool.config.sql.SqlFileReferenceExtractor
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.DriverManagerDatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.ModuleRegistrySql
import com.sbrf.lt.datapool.db.registry.sql.normalizeRegistrySchemaName
import com.sbrf.lt.platform.ui.config.UiModuleStorePostgresConfig
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import com.sbrf.lt.platform.ui.model.ModuleCatalogItemResponse
import com.sbrf.lt.platform.ui.model.ModuleValidationIssueResponse
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import java.sql.Connection
import java.sql.ResultSet
import java.security.MessageDigest
import java.util.UUID

open class DatabaseModuleStore(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String = UiModuleStorePostgresConfig.DEFAULT_SCHEMA,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) {
    private val tagsType = object : TypeReference<List<String>>() {}
    private val issuesType = object : TypeReference<List<ModuleValidationIssueResponse>>() {}
    private val sqlFilesType = object : TypeReference<List<ModuleFileContent>>() {}
    private val revisionWriter = DatabaseModuleRevisionWriter(objectMapper)

    open fun listModules(includeHidden: Boolean = false): List<ModuleCatalogItemResponse> {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(ModuleRegistrySql.catalog(normalizedSchema)).use { statement ->
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
                    requiredCredentialKeys = emptyList(),
                    missingCredentialKeys = emptyList(),
                    credentialsReady = true,
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
        val normalizedSchema = normalizeRegistrySchemaName(schema)
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
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(ModuleRegistrySql.discardWorkingCopy(normalizedSchema)).use { statement ->
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
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val moduleInfo = loadModuleForPublish(connection, normalizedSchema, moduleCode, actorId, actorSource)
                require(moduleInfo.hasWorkingCopy) {
                    "Нет личного черновика для публикации. Сначала сохраните изменения."
                }
                require(moduleInfo.baseRevisionId == moduleInfo.currentRevisionId && moduleInfo.workingCopyStatus != "STALE") {
                    "Личный черновик устарел относительно текущей ревизии. Перезагрузите модуль и примените изменения заново."
                }

                val newRevisionId = UUID.randomUUID().toString()
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
                    sqlFiles = readSqlFileContents(moduleInfo.workingCopyJson),
                )
                revisionWriter.insertRevisionStructure(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    revisionId = newRevisionId,
                    configText = moduleInfo.workingCopyYaml,
                    sqlAssetIds = sqlAssetIds,
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
        originKind: String = "CREATED_IN_UI",
        request: CreateModuleRequest,
    ): CreateModuleResult {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
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
        val normalizedSchema = normalizeRegistrySchemaName(schema)
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
        connection.prepareStatement(ModuleRegistrySql.moduleForSave(normalizedSchema)).use { statement ->
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
        connection.prepareStatement(ModuleRegistrySql.upsertWorkingCopy(normalizedSchema)).use { statement ->
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
        return buildSnapshotJson(request.configText, request.sqlFiles)
    }

    private fun buildSnapshotJson(configText: String, sqlFileContents: Map<String, String>): String {
        val sqlLabels = SqlFileReferenceExtractor.labelsByPathOrEmpty(configText, objectMapper)
        val sqlFiles = sqlFileContents.entries
            .sortedBy { it.key }
            .map { (path, content) ->
                ModuleFileContent(
                    label = sqlLabels[path] ?: path,
                    path = path,
                    content = content,
                    exists = true,
                )
            }
        val root = objectMapper.createObjectNode()
        root.put("configText", configText)
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

    private fun readWorkingCopySqlFiles(workingCopyJson: String): List<ModuleFileContent> {
        val root = objectMapper.readTree(workingCopyJson)
        val sqlFilesNode = root.path("sqlFiles").takeIf { it.isArray } ?: return emptyList()
        val sqlFiles = objectMapper.readValue<List<ModuleFileContent>>(sqlFilesNode.traverse(objectMapper), sqlFilesType)
        val configText = root.path("configText").takeIf { it.isTextual }?.asText().orEmpty()
        return relabelSqlFiles(configText, sqlFiles)
    }

    private fun readSqlFileContents(workingCopyJson: String?): Map<String, String> {
        if (workingCopyJson.isNullOrBlank()) return emptyMap()
        return readWorkingCopySqlFiles(workingCopyJson).associate { it.path to it.content }
    }

    private fun loadModuleForPublish(
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
                if (!rs.next()) error("DB-модуль '$moduleCode' не найден.")
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

    private fun updateModuleCurrentRevision(
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

    private fun deleteWorkingCopyAfterPublish(
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

    private fun insertNewModule(
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
        val snapshotJson = buildSnapshotJson(request.configText, request.sqlFiles)

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

    private fun deleteWorkingCopyForModule(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
    ) {
        connection.prepareStatement(ModuleRegistrySql.deleteWorkingCopyForModule(normalizedSchema)).use { stmt ->
            stmt.setString(1, moduleId)
            stmt.executeUpdate()
        }
    }

    private fun deleteModuleCascade(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
    ) {
        connection.prepareStatement(ModuleRegistrySql.deleteModule(normalizedSchema)).use { stmt ->
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
                connectionProvider = DriverManagerDatabaseConnectionProvider(
                    requireNotNull(config.jdbcUrl),
                    requireNotNull(config.username),
                    requireNotNull(config.password),
                ),
                schema = config.schemaName(),
            )
    }

    private fun relabelSqlFiles(configText: String, sqlFiles: List<ModuleFileContent>): List<ModuleFileContent> {
        if (sqlFiles.isEmpty()) {
            return sqlFiles
        }
        val labelsByPath = SqlFileReferenceExtractor.labelsByPathOrEmpty(configText, objectMapper)
        return sqlFiles.map { file ->
            val expectedLabel = labelsByPath[file.path]
            if (expectedLabel != null && (file.label.isBlank() || file.label == file.path)) {
                file.copy(label = expectedLabel)
            } else {
                file
            }
        }
    }
}
