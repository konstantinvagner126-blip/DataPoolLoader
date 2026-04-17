package com.sbrf.lt.datapool.module.sync

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.config.sql.SqlFileReferenceExtractor
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.model.RegistryModuleCreationResult
import com.sbrf.lt.datapool.db.registry.model.RegistryModuleDraft
import com.sbrf.lt.datapool.db.registry.sql.normalizeRegistrySchemaName
import java.nio.file.Path
import java.nio.file.Files
import java.security.MessageDigest
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

open class ModuleSyncService(
    private val connectionProvider: DatabaseConnectionProvider,
    private val moduleRegistryImporter: ModuleRegistryImporter,
    private val schema: String = "ui_registry",
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) {
    private val configMapper: ObjectMapper = ConfigLoader().objectMapper()

    open fun currentSyncState(): ModuleSyncState {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val maintenanceLockKey = advisoryLockKey("module-sync:maintenance")
            val maintenanceMode = !tryAcquireExclusiveAdvisoryLock(connection, maintenanceLockKey)
            val runningFullSyncs = loadRunningFullSyncs(connection, normalizedSchema)
            val staleFullSyncIds = mutableListOf<String>()
            val activeFullSync = if (maintenanceMode) {
                runningFullSyncs.firstOrNull()
            } else {
                runningFullSyncs.onEach { staleFullSyncIds += it.syncRunId }
                releaseExclusiveAdvisoryLock(connection, maintenanceLockKey)
                null
            }
            val activeSingleSyncs = filterActiveSingleSyncs(
                connection = connection,
                normalizedSchema = normalizedSchema,
                runningSingleSyncs = loadRunningSingleSyncs(connection, normalizedSchema),
            )
            if (staleFullSyncIds.isNotEmpty()) {
                markSyncRunsAsFailed(connection, normalizedSchema, staleFullSyncIds)
            }
            if (!maintenanceMode) {
                return ModuleSyncState(activeSingleSyncs = activeSingleSyncs)
            }

            return ModuleSyncState(
                maintenanceMode = true,
                activeFullSync = activeFullSync,
                activeSingleSyncs = activeSingleSyncs,
            )
        }
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
        val normalizedSchema = normalizeRegistrySchemaName(schema)

        val syncItem = connectionProvider.getConnection().use { lockConnection ->
            val globalLockKey = advisoryLockKey("module-sync:global")
            if (!tryAcquireSharedAdvisoryLock(lockConnection, globalLockKey)) {
                val activeFullSync = loadRunningFullSync(lockConnection, normalizedSchema)
                return syncOneLockRejected(
                    syncRunId = syncRunId,
                    moduleCode = moduleCode,
                    startedAt = startedAt,
                    message = "Работа пока невозможна: идет массовый импорт модулей в БД.",
                    activeSync = activeFullSync,
                )
            }

            val moduleLockKey = advisoryLockKey("module-sync:module:$moduleCode")
            try {
                if (!tryAcquireExclusiveAdvisoryLock(lockConnection, moduleLockKey)) {
                    val activeSingleSync = loadRunningSingleSync(lockConnection, normalizedSchema, moduleCode)
                    return syncOneLockRejected(
                        syncRunId = syncRunId,
                        moduleCode = moduleCode,
                        startedAt = startedAt,
                        message = "Работа пока невозможна: модуль '$moduleCode' уже импортируется другим пользователем.",
                        activeSync = activeSingleSync,
                    )
                }

                try {
                    insertRunningSyncRun(
                        normalizedSchema = normalizedSchema,
                        syncRunId = syncRunId,
                        scope = "ONE",
                        moduleCode = moduleCode,
                        startedAt = startedAt,
                        actorId = actorId,
                        actorSource = actorSource,
                        actorDisplayName = actorDisplayName,
                    )
                    syncSingleModule(
                        normalizedSchema = normalizedSchema,
                        moduleCode = moduleCode,
                        appsRoot = appsRoot,
                        actorId = actorId,
                        actorSource = actorSource,
                        actorDisplayName = actorDisplayName,
                    )
                } catch (e: Exception) {
                    failedSyncItem(
                        moduleCode = moduleCode,
                        exception = e,
                    )
                } finally {
                    releaseExclusiveAdvisoryLock(lockConnection, moduleLockKey)
                }
            } finally {
                releaseSharedAdvisoryLock(lockConnection, globalLockKey)
            }
        }

        val finishedAt = Instant.now()
        val status = when (syncItem.status) {
            "SUCCESS" -> "SUCCESS"
            "WARNING" -> "PARTIAL_SUCCESS"
            else -> "FAILED"
        }

        val items = listOf(syncItem)
        completeSyncRun(
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
        val normalizedSchema = normalizeRegistrySchemaName(schema)

        val moduleDirs = findModuleDirectories(appsRoot)
        val items = mutableListOf<SyncItemResult>()

        connectionProvider.getConnection().use { lockConnection ->
            val globalLockKey = advisoryLockKey("module-sync:global")
            if (!tryAcquireExclusiveAdvisoryLock(lockConnection, globalLockKey)) {
                return syncAllLockRejected(
                    syncRunId = syncRunId,
                    startedAt = startedAt,
                    message = "Работа пока невозможна: уже идет импорт модулей в БД.",
                )
            }

            val maintenanceLockKey = advisoryLockKey("module-sync:maintenance")

            try {
                if (!tryAcquireExclusiveAdvisoryLock(lockConnection, maintenanceLockKey)) {
                    return syncAllLockRejected(
                        syncRunId = syncRunId,
                        startedAt = startedAt,
                        message = "Работа пока невозможна: уже идет массовый импорт модулей в БД.",
                    )
                }

                insertRunningSyncRun(
                    normalizedSchema = normalizedSchema,
                    syncRunId = syncRunId,
                    scope = "ALL",
                    moduleCode = null,
                    startedAt = startedAt,
                    actorId = actorId,
                    actorSource = actorSource,
                    actorDisplayName = actorDisplayName,
                )

                moduleDirs.forEach { moduleDir ->
                    try {
                        val moduleCode = moduleDir.fileName?.toString()
                            ?: error("Не удалось определить module code для директории: $moduleDir")
                        val syncItem = syncSingleModule(
                            normalizedSchema = normalizedSchema,
                            moduleCode = moduleCode,
                            appsRoot = appsRoot,
                            actorId = actorId,
                            actorSource = actorSource,
                            actorDisplayName = actorDisplayName,
                        )
                        items += syncItem
                    } catch (e: Exception) {
                        items += failedSyncItem(
                            moduleCode = moduleDir.fileName?.toString() ?: "unknown",
                            exception = e,
                        )
                    }
                }
            } finally {
                releaseExclusiveAdvisoryLock(lockConnection, maintenanceLockKey)
                releaseExclusiveAdvisoryLock(lockConnection, globalLockKey)
            }
        }

        val finishedAt = Instant.now()
        val status = when {
            items.all { it.status == "SUCCESS" } -> "SUCCESS"
            items.any { it.status == "FAILED" } -> "PARTIAL_SUCCESS"
            items.any { it.status == "WARNING" } -> "PARTIAL_SUCCESS"
            else -> "SUCCESS"
        }

        completeSyncRun(
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

    private fun loadRunningFullSyncs(
        connection: Connection,
        normalizedSchema: String,
    ): List<ActiveModuleSyncRun> {
        val sql = """
            select
                sync_run_id::text as sync_run_id,
                scope,
                started_at,
                module_code,
                started_by_actor_id,
                started_by_actor_source,
                started_by_actor_display_name
            from $normalizedSchema.module_sync_run
            where scope = 'ALL'
              and status = 'RUNNING'
              and finished_at is null
            order by started_at desc
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<ActiveModuleSyncRun>()
                while (rs.next()) {
                    result += ActiveModuleSyncRun(
                        syncRunId = rs.getString("sync_run_id"),
                        scope = rs.getString("scope"),
                        startedAt = rs.getTimestamp("started_at").toInstant(),
                        moduleCode = rs.getString("module_code"),
                        startedByActorId = rs.getString("started_by_actor_id"),
                        startedByActorSource = rs.getString("started_by_actor_source"),
                        startedByActorDisplayName = rs.getString("started_by_actor_display_name"),
                    )
                }
                return result
            }
        }
    }

    private fun loadRunningFullSync(
        connection: Connection,
        normalizedSchema: String,
    ): ActiveModuleSyncRun? =
        loadRunningFullSyncs(connection, normalizedSchema).firstOrNull()

    private fun loadRunningSingleSync(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
    ): ActiveModuleSyncRun? =
        loadRunningSingleSyncs(connection, normalizedSchema)
            .firstOrNull { it.moduleCode == moduleCode }

    private fun loadRunningSingleSyncs(
        connection: Connection,
        normalizedSchema: String,
    ): List<ActiveModuleSyncRun> {
        val sql = """
            select
                sync_run_id::text as sync_run_id,
                scope,
                started_at,
                module_code,
                started_by_actor_id,
                started_by_actor_source,
                started_by_actor_display_name
            from $normalizedSchema.module_sync_run
            where scope = 'ONE'
              and status = 'RUNNING'
              and finished_at is null
            order by started_at desc
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<ActiveModuleSyncRun>()
                while (rs.next()) {
                    result += ActiveModuleSyncRun(
                        syncRunId = rs.getString("sync_run_id"),
                        scope = rs.getString("scope"),
                        startedAt = rs.getTimestamp("started_at").toInstant(),
                        moduleCode = rs.getString("module_code"),
                        startedByActorId = rs.getString("started_by_actor_id"),
                        startedByActorSource = rs.getString("started_by_actor_source"),
                        startedByActorDisplayName = rs.getString("started_by_actor_display_name"),
                    )
                }
                return result
            }
        }
    }

    private fun filterActiveSingleSyncs(
        connection: Connection,
        normalizedSchema: String,
        runningSingleSyncs: List<ActiveModuleSyncRun>,
    ): List<ActiveModuleSyncRun> {
        if (runningSingleSyncs.isEmpty()) {
            return emptyList()
        }
        val activeSyncs = mutableListOf<ActiveModuleSyncRun>()
        val staleSyncRunIds = mutableListOf<String>()
        runningSingleSyncs.forEach { sync ->
            val moduleCode = sync.moduleCode
            if (moduleCode.isNullOrBlank()) {
                staleSyncRunIds += sync.syncRunId
                return@forEach
            }
            val moduleLockKey = advisoryLockKey("module-sync:module:$moduleCode")
            if (tryAcquireExclusiveAdvisoryLock(connection, moduleLockKey)) {
                releaseExclusiveAdvisoryLock(connection, moduleLockKey)
                staleSyncRunIds += sync.syncRunId
            } else {
                activeSyncs += sync
            }
        }
        if (staleSyncRunIds.isNotEmpty()) {
            markSyncRunsAsFailed(connection, normalizedSchema, staleSyncRunIds)
        }
        return activeSyncs
    }

    private fun syncOneLockRejected(
        syncRunId: String,
        moduleCode: String,
        startedAt: Instant,
        message: String,
        activeSync: ActiveModuleSyncRun? = null,
    ): SyncRunResult {
        val finishedAt = Instant.now()
        val item = SyncItemResult(
            moduleCode = moduleCode,
            action = "FAILED",
            status = "FAILED",
            detectedHash = "",
            errorMessage = message,
            details = linkedMapOf<String, Any?>(
                "reason" to "sync_lock_not_acquired",
            ).apply {
                activeSync?.let {
                    put("activeSyncRunId", it.syncRunId)
                    put("activeSyncScope", it.scope)
                    put("activeSyncModuleCode", it.moduleCode)
                    put("activeSyncStartedAt", it.startedAt.toString())
                    put("activeSyncStartedByActorId", it.startedByActorId)
                    put("activeSyncStartedByActorDisplayName", it.startedByActorDisplayName)
                }
            },
        )
        return SyncRunResult(
            syncRunId = syncRunId,
            scope = "ONE",
            moduleCode = moduleCode,
            status = "FAILED",
            startedAt = startedAt,
            finishedAt = finishedAt,
            items = listOf(item),
            totalProcessed = 1,
            totalCreated = 0,
            totalUpdated = 0,
            totalSkipped = 0,
            totalFailed = 1,
            errorMessage = message,
        )
    }

    private fun syncAllLockRejected(
        syncRunId: String,
        startedAt: Instant,
        message: String,
    ): SyncRunResult {
        val finishedAt = Instant.now()
        return SyncRunResult(
            syncRunId = syncRunId,
            scope = "ALL",
            status = "FAILED",
            startedAt = startedAt,
            finishedAt = finishedAt,
            items = emptyList(),
            totalProcessed = 0,
            totalCreated = 0,
            totalUpdated = 0,
            totalSkipped = 0,
            totalFailed = 0,
            errorMessage = message,
        )
    }

    private fun tryAcquireExclusiveAdvisoryLock(connection: Connection, lockKey: Long): Boolean =
        queryAdvisoryLock(connection, "select pg_try_advisory_lock(?) as acquired", lockKey)

    private fun tryAcquireSharedAdvisoryLock(connection: Connection, lockKey: Long): Boolean =
        queryAdvisoryLock(connection, "select pg_try_advisory_lock_shared(?) as acquired", lockKey)

    private fun releaseExclusiveAdvisoryLock(connection: Connection, lockKey: Long) {
        queryAdvisoryLock(connection, "select pg_advisory_unlock(?) as acquired", lockKey)
    }

    private fun releaseSharedAdvisoryLock(connection: Connection, lockKey: Long) {
        queryAdvisoryLock(connection, "select pg_advisory_unlock_shared(?) as acquired", lockKey)
    }

    private fun queryAdvisoryLock(connection: Connection, sql: String, lockKey: Long): Boolean {
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, lockKey)
            stmt.executeQuery().use { rs ->
                return rs.next() && rs.getBoolean("acquired")
            }
        }
    }

    private fun advisoryLockKey(value: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        var result = 0L
        repeat(8) { index ->
            result = (result shl 8) or (digest[index].toLong() and 0xff)
        }
        return result
    }

    private fun syncSingleModule(
        normalizedSchema: String,
        moduleCode: String,
        appsRoot: Path,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): SyncItemResult {
        val moduleDir = appsRoot.resolve(moduleCode)
        val configFile = moduleConfigFile(moduleDir)
        val previousSyncItem = loadPreviousSyncItem(normalizedSchema, moduleCode)
        val precheckFingerprint = collectFilesystemFingerprint(
            moduleDir = moduleDir,
            trackedPaths = linkedSetOf<String>().apply {
                add(moduleConfigRelativePath())
                add("ui-module.yml")
                previousSyncItem?.filesystemFingerprint?.trackedFiles
                    ?.mapTo(this) { it.path }
            },
        )

        if (!configFile.toFile().exists()) {
            return SyncItemResult(
                moduleCode = moduleCode,
                action = "FAILED",
                status = "FAILED",
                detectedHash = contentHash("missing:application_yml:$moduleCode", emptyMap()),
                errorMessage = "application.yml не найден: ${configFile.toAbsolutePath()}",
                details = syncDetails(
                    reason = "application_yml_missing",
                    filesystemFingerprint = precheckFingerprint,
                ),
            )
        }

        val existingModule = loadExistingModule(normalizedSchema, moduleCode)
        val fastResult = buildFastPrecheckResult(
            moduleCode = moduleCode,
            existingModule = existingModule,
            previousSyncItem = previousSyncItem,
            precheckFingerprint = precheckFingerprint,
        )
        if (fastResult != null) {
            return fastResult
        }

        val configText = configFile.toFile().readText()
        val sqlFiles = loadSqlFiles(moduleDir, configText)
        val detectedHash = contentHash(configText, sqlFiles)
        val fullFingerprint = collectFilesystemFingerprint(
            moduleDir = moduleDir,
            trackedPaths = trackedPathsFromConfig(moduleDir, configText),
        )

        if (existingModule != null) {
            if (existingModule.contentHash == detectedHash) {
                return SyncItemResult(
                    moduleCode = moduleCode,
                    action = "SKIPPED",
                    status = "SUCCESS",
                    detectedHash = detectedHash,
                    resultRevisionId = existingModule.currentRevisionId,
                    details = syncDetails(
                        reason = "content_hash_match",
                        filesystemFingerprint = fullFingerprint,
                    ),
                )
            }

            return SyncItemResult(
                moduleCode = moduleCode,
                action = "SKIPPED_CODE_CONFLICT",
                status = "WARNING",
                detectedHash = detectedHash,
                resultRevisionId = existingModule.currentRevisionId,
                details = syncDetails(
                    reason = "module_code_already_exists",
                    filesystemFingerprint = fullFingerprint,
                    extra = mapOf(
                        "message" to "DB-модуль с таким кодом уже существует. Импорт не перезаписывает существующие модули.",
                    ),
                ),
            )
        }

        val newModuleResult = createNewModuleFromFiles(
            normalizedSchema = normalizedSchema,
            moduleCode = moduleCode,
            configText = configText,
            sqlFiles = sqlFiles,
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
            details = syncDetails(
                reason = "module_created",
                filesystemFingerprint = fullFingerprint,
            ),
        )
    }

    private fun buildFastPrecheckResult(
        moduleCode: String,
        existingModule: ExistingModule?,
        previousSyncItem: PreviousModuleSyncItem?,
        precheckFingerprint: ModuleSyncFileFingerprint,
    ): SyncItemResult? {
        if (existingModule == null || previousSyncItem == null) {
            return null
        }
        if (previousSyncItem.status == "FAILED" || previousSyncItem.detectedHash.isBlank()) {
            return null
        }
        if (previousSyncItem.filesystemFingerprint != precheckFingerprint) {
            return null
        }

        val hashesMatch = existingModule.contentHash == previousSyncItem.detectedHash
        return if (hashesMatch) {
            SyncItemResult(
                moduleCode = moduleCode,
                action = "SKIPPED",
                status = "SUCCESS",
                detectedHash = previousSyncItem.detectedHash,
                resultRevisionId = existingModule.currentRevisionId,
                details = syncDetails(
                    reason = "fingerprint_match",
                    filesystemFingerprint = precheckFingerprint,
                    extra = mapOf("precheckMatched" to true),
                ),
            )
        } else {
            SyncItemResult(
                moduleCode = moduleCode,
                action = "SKIPPED_CODE_CONFLICT",
                status = "WARNING",
                detectedHash = previousSyncItem.detectedHash,
                resultRevisionId = existingModule.currentRevisionId,
                details = syncDetails(
                    reason = "module_code_already_exists",
                    filesystemFingerprint = precheckFingerprint,
                    extra = mapOf(
                        "precheckMatched" to true,
                        "message" to "DB-модуль с таким кодом уже существует. Импорт не перезаписывает существующие модули.",
                    ),
                ),
            )
        }
    }

    private fun createNewModuleFromFiles(
        normalizedSchema: String,
        moduleCode: String,
        configText: String,
        sqlFiles: Map<String, String>,
        moduleDir: Path,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): RegistryModuleCreationResult {
        val metadata = loadModuleUiMetadata(moduleDir, moduleCode)

        val request = RegistryModuleDraft(
            title = metadata.title,
            description = metadata.description,
            tags = metadata.tags,
            sqlFiles = sqlFiles,
            configText = configText,
            hiddenFromUi = metadata.hiddenFromUi,
        )

        return moduleRegistryImporter.createModule(
            moduleCode = moduleCode,
            actorId = actorId,
            actorSource = actorSource,
            actorDisplayName = actorDisplayName,
            originKind = "IMPORTED_FROM_FILES",
            draft = request,
        )
    }

    private fun loadExistingModule(
        normalizedSchema: String,
        moduleCode: String,
    ): ExistingModule? {
        connectionProvider.getConnection().use { connection ->
            return loadExistingModuleWithConnection(connection, normalizedSchema, moduleCode)
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
                    contentHash = rs.getString("content_hash"),
                )
            }
        }
    }

    private fun loadPreviousSyncItem(
        normalizedSchema: String,
        moduleCode: String,
    ): PreviousModuleSyncItem? {
        connectionProvider.getConnection().use { localConnection ->
            return loadPreviousSyncItemWithConnection(localConnection, normalizedSchema, moduleCode)
        }
    }

    private fun loadPreviousSyncItemWithConnection(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
    ): PreviousModuleSyncItem? {
        val sql = """
            select
                i.action,
                i.status,
                i.detected_hash,
                i.result_revision_id::text as result_revision_id,
                i.details::text as details
            from $normalizedSchema.module_sync_run_item i
            join $normalizedSchema.module_sync_run r
                on r.sync_run_id = i.sync_run_id
            where i.module_code = ?
            order by r.started_at desc, i.sync_run_item_id desc
            limit 1
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, moduleCode)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) return null
                val detailsJson = rs.getString("details")
                val fingerprint = runCatching {
                    val root = objectMapper.readTree(detailsJson)
                    root.get("filesystemFingerprint")?.let { node ->
                        objectMapper.treeToValue(node, ModuleSyncFileFingerprint::class.java)
                    }
                }.getOrNull()
                return PreviousModuleSyncItem(
                    action = rs.getString("action"),
                    status = rs.getString("status"),
                    detectedHash = rs.getString("detected_hash"),
                    resultRevisionId = rs.getString("result_revision_id"),
                    filesystemFingerprint = fingerprint,
                )
            }
        }
    }

    private fun insertRunningSyncRun(
        normalizedSchema: String,
        syncRunId: String,
        scope: String,
        moduleCode: String?,
        startedAt: Instant,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ) {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(
                """
                    insert into $normalizedSchema.module_sync_run (
                        sync_run_id, started_at, finished_at, started_by_actor_id,
                        started_by_actor_source, started_by_actor_display_name,
                        status, scope, module_code
                    ) values (?::uuid, ?, null, ?, ?, ?, 'RUNNING', ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, syncRunId)
                stmt.setTimestamp(2, Timestamp.from(startedAt))
                stmt.setString(3, actorId)
                stmt.setString(4, actorSource)
                stmt.setString(5, actorDisplayName)
                stmt.setString(6, scope)
                stmt.setString(7, moduleCode)
                stmt.executeUpdate()
            }
        }
    }

    private fun completeSyncRun(
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
                updateSyncRunRecord(
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

    private fun updateSyncRunRecord(
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
            update $normalizedSchema.module_sync_run
            set
                started_at = ?,
                finished_at = ?,
                started_by_actor_id = ?,
                started_by_actor_source = ?,
                started_by_actor_display_name = ?,
                status = ?,
                scope = ?,
                module_code = ?
            where sync_run_id = ?::uuid
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, Timestamp.from(startedAt))
            stmt.setObject(2, Timestamp.from(finishedAt))
            stmt.setString(3, actorId)
            stmt.setString(4, actorSource)
            stmt.setString(5, actorDisplayName)
            stmt.setString(6, status)
            stmt.setString(7, scope)
            stmt.setString(8, moduleCode)
            stmt.setString(9, syncRunId)
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

    private fun markSyncRunsAsFailed(
        connection: Connection,
        normalizedSchema: String,
        syncRunIds: List<String>,
    ) {
        if (syncRunIds.isEmpty()) {
            return
        }
        val placeholders = syncRunIds.joinToString(", ") { "?::uuid" }
        val sql = """
            update $normalizedSchema.module_sync_run
            set
                finished_at = coalesce(finished_at, ?),
                status = 'FAILED'
            where status = 'RUNNING'
              and sync_run_id in ($placeholders)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setTimestamp(1, Timestamp.from(Instant.now()))
            syncRunIds.forEachIndexed { index, syncRunId ->
                stmt.setString(index + 2, syncRunId)
            }
            stmt.executeUpdate()
        }
    }

    private fun findModuleDirectories(appsRoot: Path): List<Path> {
        if (!appsRoot.toFile().exists()) return emptyList()
        return appsRoot.toFile().listFiles { file -> file.isDirectory }
            ?.map { it.toPath() }
            ?.filter { moduleConfigFile(it).toFile().exists() }
            ?: emptyList()
    }

    private fun loadModuleUiMetadata(moduleDir: Path, moduleCode: String): ModuleUiMetadata {
        val metadataFile = moduleDir.resolve("ui-module.yml").toFile()
        if (!metadataFile.exists()) {
            return ModuleUiMetadata(title = moduleCode)
        }

        return try {
            val root = configMapper.readTree(metadataFile)
            ModuleUiMetadata(
                title = root.path("title")
                    .takeIf { it.isTextual }
                    ?.asText()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: moduleCode,
                description = root.path("description")
                    .takeIf { it.isTextual }
                    ?.asText()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                tags = root.path("tags")
                    .takeIf { it.isArray }
                    ?.mapNotNull { node ->
                        node.takeIf { it.isTextual }
                            ?.asText()
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                    }
                    .orEmpty(),
                hiddenFromUi = root.path("hiddenFromUi").takeIf { it.isBoolean }?.asBoolean() ?: false,
            )
        } catch (e: Exception) {
            ModuleUiMetadata(title = moduleCode)
        }
    }

    private fun loadSqlFiles(moduleDir: Path, configText: String): Map<String, String> {
        val configFile = moduleConfigFile(moduleDir)
        val resourcesDir = moduleResourcesDir(moduleDir)
        return SqlFileReferenceExtractor.extractOrEmpty(configText, configMapper)
            .mapNotNull { entry ->
                val file = resolveSqlPath(configFile, resourcesDir, entry.path)?.toFile()
                if (file != null && file.isFile) entry.path to file.readText() else null
            }
            .toMap()
    }

    private fun trackedPathsFromConfig(moduleDir: Path, configText: String): LinkedHashSet<String> {
        val configFile = moduleConfigFile(moduleDir)
        val resourcesDir = moduleResourcesDir(moduleDir)
        return linkedSetOf<String>().apply {
            add(moduleConfigRelativePath())
            add("ui-module.yml")
            SqlFileReferenceExtractor.extractOrEmpty(configText, configMapper).forEach { entry ->
                resolveSqlPath(configFile, resourcesDir, entry.path)?.let { resolvedPath ->
                    add(trackedPathKey(moduleDir, resolvedPath))
                }
            }
        }
    }

    private fun collectFilesystemFingerprint(
        moduleDir: Path,
        trackedPaths: Set<String>,
    ): ModuleSyncFileFingerprint {
        val trackedFiles = trackedPaths
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .map { trackedPath ->
                val file = resolveTrackedPath(moduleDir, trackedPath)
                val exists = Files.exists(file) && Files.isRegularFile(file)
                ModuleSyncTrackedFile(
                    path = trackedPath,
                    exists = exists,
                    lastModifiedEpochMillis = file.takeIf { exists }?.let { Files.getLastModifiedTime(it).toMillis() },
                    sizeBytes = file.takeIf { exists }?.let { Files.size(it) },
                )
            }
        return ModuleSyncFileFingerprint(trackedFiles = trackedFiles)
    }

    private fun resolveTrackedPath(moduleDir: Path, trackedPath: String): Path {
        val path = Path.of(trackedPath)
        return if (path.isAbsolute) path.normalize() else moduleDir.resolve(trackedPath).normalize()
    }

    private fun trackedPathKey(moduleDir: Path, file: Path): String {
        val normalizedFile = file.normalize()
        val normalizedModuleDir = moduleDir.normalize()
        return if (normalizedFile.startsWith(normalizedModuleDir)) {
            normalizedModuleDir.relativize(normalizedFile).toString()
        } else {
            normalizedFile.toString()
        }
    }

    private fun moduleResourcesDir(moduleDir: Path): Path =
        moduleDir.resolve("src/main/resources").normalize()

    private fun moduleConfigFile(moduleDir: Path): Path =
        moduleResourcesDir(moduleDir).resolve("application.yml").normalize()

    private fun moduleConfigRelativePath(): String = "src/main/resources/application.yml"

    private fun syncDetails(
        reason: String,
        filesystemFingerprint: ModuleSyncFileFingerprint,
        extra: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> = linkedMapOf<String, Any?>(
        "reason" to reason,
        "filesystemFingerprint" to objectMapper.convertValue(filesystemFingerprint, Map::class.java),
    ).apply {
        putAll(extra)
    }

    private fun resolveSqlPath(configFile: Path, resourcesDir: Path, sqlRef: String): Path? {
        val trimmed = sqlRef.trim()
        if (trimmed.isBlank()) return null
        return if (trimmed.startsWith("classpath:")) {
            resourcesDir.resolve(trimmed.removePrefix("classpath:").removePrefix("/")).normalize()
        } else {
            val path = Path.of(trimmed)
            if (path.isAbsolute) path else configFile.parent.resolve(path).normalize()
        }
    }

    private fun failedSyncItem(
        moduleCode: String,
        exception: Exception,
    ): SyncItemResult {
        val errorMessage = exception.message
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: (exception::class.qualifiedName ?: "sync_exception")
        return SyncItemResult(
            moduleCode = moduleCode,
            action = "FAILED",
            status = "FAILED",
            detectedHash = contentHash(
                "sync_exception:$moduleCode:${exception::class.qualifiedName.orEmpty()}:$errorMessage",
                emptyMap(),
            ),
            errorMessage = errorMessage,
            details = linkedMapOf(
                "reason" to "sync_exception",
                "exceptionClass" to (exception::class.qualifiedName ?: exception::class.simpleName ?: "unknown"),
            ),
        )
    }

    private fun contentHash(configText: String, sqlFiles: Map<String, String>): String {
        val input = buildString {
            append(configText)
            sqlFiles.toSortedMap().forEach { (path, content) ->
                append('\n')
                append(path)
                append('\u0000')
                append(content)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
