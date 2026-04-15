package com.sbrf.lt.platform.ui.sync

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.model.RootConfig
import com.sbrf.lt.platform.ui.config.UiModuleStorePostgresConfig
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.module.DatabaseConnectionProvider
import com.sbrf.lt.platform.ui.module.DatabaseModuleStore
import com.sbrf.lt.platform.ui.module.DriverManagerDatabaseConnectionProvider
import com.sbrf.lt.platform.ui.module.CreateModuleRequest
import com.sbrf.lt.platform.ui.module.CreateModuleResult
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.time.Instant
import java.util.UUID

data class ModuleSyncConfig(
    val appsRoot: Path,
    val includeHidden: Boolean = false,
)

data class SyncRunResult(
    val syncRunId: String,
    val scope: String,
    val moduleCode: String? = null,
    val status: String,
    val startedAt: Instant,
    val finishedAt: Instant,
    val items: List<SyncItemResult>,
    val totalProcessed: Int,
    val totalCreated: Int,
    val totalUpdated: Int,
    val totalSkipped: Int,
    val totalFailed: Int,
    val errorMessage: String? = null,
)

data class SyncItemResult(
    val moduleCode: String,
    val action: String,
    val status: String,
    val detectedHash: String,
    val resultRevisionId: String? = null,
    val errorMessage: String? = null,
    val details: Map<String, Any?> = emptyMap(),
)

open class ModuleSyncService(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String = "ui_registry",
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) {
    companion object {
        fun fromConfig(config: UiModuleStorePostgresConfig): ModuleSyncService =
            ModuleSyncService(
                connectionProvider = DriverManagerDatabaseConnectionProvider(config),
                schema = config.schemaName(),
            )
    }
    open fun syncOneFromFiles(
        moduleCode: String,
        appsRoot: Path,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): SyncRunResult {
        val syncRunId = UUID.randomUUID().toString()
        val startedAt = Instant.now()
        val normalizedSchema = normalizeSchemaName(schema)

        val syncItem = try {
            syncSingleModule(
                connection = null,
                normalizedSchema = normalizedSchema,
                moduleCode = moduleCode,
                appsRoot = appsRoot,
                actorId = actorId,
                actorSource = actorSource,
                actorDisplayName = actorDisplayName,
            )
        } catch (e: Exception) {
            SyncItemResult(
                moduleCode = moduleCode,
                action = "FAILED",
                status = "FAILED",
                detectedHash = "",
                errorMessage = e.message,
            )
        }

        val finishedAt = Instant.now()
        val status = when (syncItem.status) {
            "SUCCESS" -> "SUCCESS"
            "WARNING" -> "PARTIAL_SUCCESS"
            else -> "FAILED"
        }

        val items = listOf(syncItem)
        recordSyncRun(
            normalizedSchema = normalizedSchema,
            syncRunId = syncRunId,
            scope = "ONE",
            moduleCode = moduleCode,
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = status,
            actorId = actorId,
            actorSource = actorSource,
            actorDisplayName = actorDisplayName,
            items = items,
        )

        return SyncRunResult(
            syncRunId = syncRunId,
            scope = "ONE",
            moduleCode = moduleCode,
            status = status,
            startedAt = startedAt,
            finishedAt = finishedAt,
            items = items,
            totalProcessed = items.size,
            totalCreated = items.count { it.action == "CREATED" },
            totalUpdated = items.count { it.action == "UPDATED" },
            totalSkipped = items.count { it.action.startsWith("SKIPPED") },
            totalFailed = items.count { it.status == "FAILED" },
        )
    }

    open fun syncAllFromFiles(
        appsRoot: Path,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): SyncRunResult {
        val syncRunId = UUID.randomUUID().toString()
        val startedAt = Instant.now()
        val normalizedSchema = normalizeSchemaName(schema)

        val moduleDirs = findModuleDirectories(appsRoot)
        val items = mutableListOf<SyncItemResult>()

        moduleDirs.forEach { moduleDir ->
            try {
                val moduleCode = moduleDir.fileName?.toString()
                    ?: error("Не удалось определить module code для директории: $moduleDir")
                val syncItem = syncSingleModule(
                    connection = null,
                    normalizedSchema = normalizedSchema,
                    moduleCode = moduleCode,
                    appsRoot = appsRoot,
                    actorId = actorId,
                    actorSource = actorSource,
                    actorDisplayName = actorDisplayName,
                )
                items += syncItem
            } catch (e: Exception) {
                items += SyncItemResult(
                    moduleCode = moduleDir.fileName?.toString() ?: "unknown",
                    action = "FAILED",
                    status = "FAILED",
                    detectedHash = "",
                    errorMessage = e.message,
                )
            }
        }

        val finishedAt = Instant.now()
        val status = when {
            items.all { it.status == "SUCCESS" } -> "SUCCESS"
            items.any { it.status == "FAILED" } -> "PARTIAL_SUCCESS"
            else -> "SUCCESS"
        }

        recordSyncRun(
            normalizedSchema = normalizedSchema,
            syncRunId = syncRunId,
            scope = "ALL",
            moduleCode = null,
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = status,
            actorId = actorId,
            actorSource = actorSource,
            actorDisplayName = actorDisplayName,
            items = items,
        )

        return SyncRunResult(
            syncRunId = syncRunId,
            scope = "ALL",
            status = status,
            startedAt = startedAt,
            finishedAt = finishedAt,
            items = items,
            totalProcessed = items.size,
            totalCreated = items.count { it.action == "CREATED" },
            totalUpdated = items.count { it.action == "UPDATED" },
            totalSkipped = items.count { it.action.startsWith("SKIPPED") },
            totalFailed = items.count { it.status == "FAILED" },
        )
    }

    private fun syncSingleModule(
        connection: Connection?,
        normalizedSchema: String,
        moduleCode: String,
        appsRoot: Path,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): SyncItemResult {
        val moduleDir = appsRoot.resolve(moduleCode)
        val configFile = moduleDir.resolve("application.yml")

        if (!configFile.toFile().exists()) {
            return SyncItemResult(
                moduleCode = moduleCode,
                action = "FAILED",
                status = "FAILED",
                detectedHash = "",
                errorMessage = "application.yml не найден: ${configFile.toAbsolutePath()}",
            )
        }

        val configText = configFile.toFile().readText()
        val detectedHash = contentHash(configText)

        val existingModule = loadExistingModule(connection, normalizedSchema, moduleCode)

        if (existingModule != null) {
            if (existingModule.contentHash == detectedHash) {
                return SyncItemResult(
                    moduleCode = moduleCode,
                    action = "SKIPPED",
                    status = "SUCCESS",
                    detectedHash = detectedHash,
                    resultRevisionId = existingModule.currentRevisionId,
                    details = mapOf("reason" to "content_hash_match"),
                )
            }

            val updatedRevisionId = updateExistingModule(
                connection = connection,
                normalizedSchema = normalizedSchema,
                moduleCode = moduleCode,
                existingModule = existingModule,
                configText = configText,
                detectedHash = detectedHash,
                moduleDir = moduleDir,
                actorId = actorId,
                actorSource = actorSource,
                actorDisplayName = actorDisplayName,
            )

            return SyncItemResult(
                moduleCode = moduleCode,
                action = "UPDATED",
                status = "SUCCESS",
                detectedHash = detectedHash,
                resultRevisionId = updatedRevisionId,
            )
        }

        val newModuleResult = createNewModuleFromFiles(
            connection = connection,
            normalizedSchema = normalizedSchema,
            moduleCode = moduleCode,
            configText = configText,
            moduleDir = moduleDir,
            actorId = actorId,
            actorSource = actorSource,
            actorDisplayName = actorDisplayName,
        )

        return SyncItemResult(
            moduleCode = moduleCode,
            action = "CREATED",
            status = "SUCCESS",
            detectedHash = detectedHash,
            resultRevisionId = newModuleResult.revisionId,
        )
    }

    private fun createNewModuleFromFiles(
        connection: Connection?,
        normalizedSchema: String,
        moduleCode: String,
        configText: String,
        moduleDir: Path,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): CreateModuleResult {
        val config = loadConfig(configText)
        val title = config.app.title ?: moduleCode
        val description = config.app.description

        val request = CreateModuleRequest(
            title = title,
            description = description,
            tags = config.app.tags ?: emptyList(),
            configText = configText,
        )

        return if (connection != null) {
            val store = DatabaseModuleStore(connectionProvider = object : DatabaseConnectionProvider {
                override fun getConnection() = connection
            }, schema = normalizedSchema)
            store.createModule(
                moduleCode = moduleCode,
                actorId = actorId,
                actorSource = actorSource,
                actorDisplayName = actorDisplayName,
                request = request,
            )
        } else {
            createModuleDirectly(
                normalizedSchema = normalizedSchema,
                moduleCode = moduleCode,
                request = request,
                actorId = actorId,
                actorSource = actorSource,
                actorDisplayName = actorDisplayName,
            )
        }
    }

    private fun updateExistingModule(
        connection: Connection?,
        normalizedSchema: String,
        moduleCode: String,
        existingModule: ExistingModule,
        configText: String,
        detectedHash: String,
        moduleDir: Path,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): String {
        val config = loadConfig(configText)
        val title = config.app.title ?: moduleCode
        val description = config.app.description

        val newRevisionId = UUID.randomUUID().toString()
        val newRevisionNo = existingModule.maxRevisionNo + 1

        if (connection != null) {
            insertNewRevisionDirectly(
                connection = connection,
                normalizedSchema = normalizedSchema,
                revisionId = newRevisionId,
                moduleId = existingModule.moduleId,
                revisionNo = newRevisionNo,
                actorDisplayName = actorDisplayName ?: actorId,
                title = title,
                description = description,
                tags = config.app.tags ?: emptyList(),
                configText = configText,
                contentHash = detectedHash,
            )
        } else {
            updateModuleDirectly(
                normalizedSchema = normalizedSchema,
                moduleCode = moduleCode,
                moduleId = existingModule.moduleId,
                newRevisionId = newRevisionId,
                newRevisionNo = newRevisionNo,
                title = title,
                description = description,
                tags = config.app.tags ?: emptyList(),
                configText = configText,
                contentHash = detectedHash,
            )
        }

        return newRevisionId
    }

    private fun loadExistingModule(
        connection: Connection?,
        normalizedSchema: String,
        moduleCode: String,
    ): ExistingModule? {
        return if (connection != null) {
            loadExistingModuleWithConnection(connection, normalizedSchema, moduleCode)
        } else {
            loadExistingModuleDirectly(normalizedSchema, moduleCode)
        }
    }

    private fun loadExistingModuleWithConnection(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
    ): ExistingModule? {
        val sql = """
            select
                m.module_id::text as module_id,
                m.current_revision_id::text as current_revision_id,
                r.revision_no as max_revision_no,
                r.content_hash as content_hash
            from $normalizedSchema.module m
            join $normalizedSchema.module_revision r
                on r.module_id = m.module_id
                and r.revision_id = m.current_revision_id
            where m.module_code = ?
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, moduleCode)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return null
                return ExistingModule(
                    moduleId = rs.getString("module_id"),
                    currentRevisionId = rs.getString("current_revision_id"),
                    maxRevisionNo = rs.getLong("max_revision_no"),
                    contentHash = rs.getString("content_hash"),
                )
            }
        }
    }

    private fun loadExistingModuleDirectly(
        normalizedSchema: String,
        moduleCode: String,
    ): ExistingModule? {
        connectionProvider.getConnection().use { connection ->
            return loadExistingModuleWithConnection(connection, normalizedSchema, moduleCode)
        }
    }

    private fun createModuleDirectly(
        normalizedSchema: String,
        moduleCode: String,
        request: CreateModuleRequest,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): CreateModuleResult {
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val store = DatabaseModuleStore(connectionProvider = object : DatabaseConnectionProvider {
                    override fun getConnection() = connection
                }, schema = normalizedSchema)
                val result = store.createModule(
                    moduleCode = moduleCode,
                    actorId = actorId,
                    actorSource = actorSource,
                    actorDisplayName = actorDisplayName,
                    request = request,
                )
                connection.commit()
                return result
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    private fun updateModuleDirectly(
        normalizedSchema: String,
        moduleCode: String,
        moduleId: String,
        newRevisionId: String,
        newRevisionNo: Long,
        title: String,
        description: String?,
        tags: List<String>,
        configText: String,
        contentHash: String,
    ) {
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                insertNewRevisionDirectly(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    revisionId = newRevisionId,
                    moduleId = moduleId,
                    revisionNo = newRevisionNo,
                    actorDisplayName = "sync",
                    title = title,
                    description = description,
                    tags = tags,
                    configText = configText,
                    contentHash = contentHash,
                )
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    private fun insertNewRevisionDirectly(
        connection: Connection,
        normalizedSchema: String,
        revisionId: String,
        moduleId: String,
        revisionNo: Long,
        actorDisplayName: String,
        title: String,
        description: String?,
        tags: List<String>,
        configText: String,
        contentHash: String,
    ) {
        val tagsArray = objectMapper.createArrayNode()
        tags.forEach { tagsArray.add(it) }

        val snapshotJson = objectMapper.createObjectNode()
        snapshotJson.put("configText", configText)
        snapshotJson.set<com.fasterxml.jackson.databind.JsonNode>("sqlFiles", objectMapper.createArrayNode())

        val sql = """
            insert into $normalizedSchema.module_revision (
                revision_id, module_id, revision_no, created_by, revision_source,
                title, description, tags, hidden_from_ui, validation_status, validation_issues,
                output_dir, file_format, merge_mode, error_mode, parallelism, fetch_size,
                query_timeout_sec, progress_log_every_rows, max_merged_rows,
                delete_output_files_after_completion, snapshot_json, snapshot_yaml, content_hash
            ) values (
                ?::uuid, ?::uuid, ?, ?, 'SYNC_FROM_FILES', ?, ?, ?::jsonb, false, 'VALID', '[]'::jsonb,
                'output', 'CSV', 'PLAIN', 'CONTINUE_ON_ERROR', 4, 1000,
                null, 10000, null, false, ?::jsonb, ?, ?
            )
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, revisionId)
            stmt.setString(2, moduleId)
            stmt.setLong(3, revisionNo)
            stmt.setString(4, actorDisplayName)
            stmt.setString(5, title)
            stmt.setString(6, description)
            stmt.setString(7, tagsArray.toString())
            stmt.setString(8, snapshotJson.toString())
            stmt.setString(9, configText)
            stmt.setString(10, contentHash)
            stmt.executeUpdate()
        }

        updateCurrentRevisionDirectly(connection, normalizedSchema, moduleId, revisionId)
    }

    private fun updateCurrentRevisionDirectly(
        connection: Connection,
        normalizedSchema: String,
        moduleId: String,
        revisionId: String,
    ) {
        val sql = """
            update $normalizedSchema.module
            set current_revision_id = ?::uuid, updated_at = now()
            where module_id = ?::uuid
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, revisionId)
            stmt.setString(2, moduleId)
            stmt.executeUpdate()
        }
    }

    private fun recordSyncRun(
        normalizedSchema: String,
        syncRunId: String,
        scope: String,
        moduleCode: String?,
        startedAt: Instant,
        finishedAt: Instant,
        status: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        items: List<SyncItemResult>,
    ) {
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                insertSyncRunRecord(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    syncRunId = syncRunId,
                    scope = scope,
                    moduleCode = moduleCode,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    status = status,
                    actorId = actorId,
                    actorSource = actorSource,
                    actorDisplayName = actorDisplayName,
                )

                items.forEach { item ->
                    insertSyncItemRecord(
                        connection = connection,
                        normalizedSchema = normalizedSchema,
                        syncRunId = syncRunId,
                        item = item,
                    )
                }

                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    private fun insertSyncRunRecord(
        connection: Connection,
        normalizedSchema: String,
        syncRunId: String,
        scope: String,
        moduleCode: String?,
        startedAt: Instant,
        finishedAt: Instant,
        status: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ) {
        val sql = """
            insert into $normalizedSchema.module_sync_run (
                sync_run_id, started_at, finished_at, started_by_actor_id,
                started_by_actor_source, started_by_actor_display_name,
                status, scope, module_code
            ) values (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, syncRunId)
            stmt.setObject(2, java.sql.Timestamp.from(startedAt))
            stmt.setObject(3, java.sql.Timestamp.from(finishedAt))
            stmt.setString(4, actorId)
            stmt.setString(5, actorSource)
            stmt.setString(6, actorDisplayName)
            stmt.setString(7, status)
            stmt.setString(8, scope)
            stmt.setString(9, moduleCode)
            stmt.executeUpdate()
        }
    }

    private fun insertSyncItemRecord(
        connection: Connection,
        normalizedSchema: String,
        syncRunId: String,
        item: SyncItemResult,
    ) {
        val itemSql = """
            insert into $normalizedSchema.module_sync_run_item (
                sync_run_item_id, sync_run_id, module_code, action, status,
                detected_hash, result_revision_id, details
            ) values (?::uuid, ?::uuid, ?, ?, ?, ?, ?::uuid, ?::jsonb)
        """.trimIndent()

        connection.prepareStatement(itemSql).use { stmt ->
            stmt.setString(1, UUID.randomUUID().toString())
            stmt.setString(2, syncRunId)
            stmt.setString(3, item.moduleCode)
            stmt.setString(4, item.action)
            stmt.setString(5, item.status)
            stmt.setString(6, item.detectedHash)
            stmt.setString(7, item.resultRevisionId)
            stmt.setString(8, objectMapper.writeValueAsString(item.details))
            stmt.executeUpdate()
        }
    }

    private fun findModuleDirectories(appsRoot: Path): List<Path> {
        if (!appsRoot.toFile().exists()) return emptyList()
        return appsRoot.toFile().listFiles { file -> file.isDirectory }
            ?.map { it.toPath() }
            ?.filter { it.resolve("application.yml").toFile().exists() }
            ?: emptyList()
    }

    private fun loadConfig(configText: String): RootConfig {
        return try {
            ConfigLoader().objectMapper().readValue(configText, RootConfig::class.java)
        } catch (e: Exception) {
            RootConfig(app = com.sbrf.lt.datapool.model.AppConfig())
        }
    }

    private fun contentHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun normalizeSchemaName(schema: String): String {
        val normalized = schema.trim()
        require(Regex("[A-Za-z_][A-Za-z0-9_]*").matches(normalized)) {
            "Некорректное имя schema PostgreSQL registry: $schema"
        }
        return normalized
    }
}

data class ExistingModule(
    val moduleId: String,
    val currentRevisionId: String,
    val maxRevisionNo: Long,
    val contentHash: String?,
)
