package com.sbrf.lt.platform.ui.sync

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.model.RegistryModuleCreationResult
import com.sbrf.lt.datapool.db.registry.model.RegistryModuleDraft
import com.sbrf.lt.datapool.module.sync.ModuleSyncService
import com.sbrf.lt.datapool.module.sync.ModuleRegistryImporter
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import kotlin.io.path.setLastModifiedTime
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModuleSyncServiceTest {

    private val noopImporter = object : ModuleRegistryImporter {
        override fun createModule(
            moduleCode: String,
            actorId: String,
            actorSource: String,
            actorDisplayName: String?,
            originKind: String,
            draft: RegistryModuleDraft,
        ): RegistryModuleCreationResult = RegistryModuleCreationResult(
            moduleId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            moduleCode = moduleCode,
            revisionId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            workingCopyId = "cccccccc-cccc-cccc-cccc-cccccccccccc",
        )
    }

    private fun moduleResourcesDir(moduleDir: java.nio.file.Path): java.nio.file.Path =
        moduleDir.resolve("src/main/resources").createDirectories()

    @Test
    fun `sync one skips existing module code conflict without creating new revision`() {
        val appsRoot = Files.createTempDirectory("ui-sync-apps-")
        val moduleDir = appsRoot.resolve("db-demo").createDirectories()
        moduleResourcesDir(moduleDir).resolve("application.yml").writeText(
            """
            app:
              outputDir: ./out
              sources: []
            """.trimIndent(),
        )

        val preparedSql = mutableListOf<String>()
        val stringParams = mutableListOf<String?>()
        val service = ModuleSyncService(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(
                    preparedSql = preparedSql,
                    stringParams = stringParams,
                    existingRows = listOf(
                        mapOf(
                            "module_id" to "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                            "current_revision_id" to "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                            "content_hash" to "old-content-hash",
                        ),
                    ),
                )
            },
            moduleRegistryImporter = noopImporter,
        )

        val result = service.syncOneFromFiles(
            moduleCode = "db-demo",
            appsRoot = appsRoot,
            actorId = "kwdev",
            actorSource = "OS_LOGIN",
            actorDisplayName = "kwdev",
        )

        val item = result.items.single()
        assertEquals("PARTIAL_SUCCESS", result.status)
        assertEquals("SKIPPED_CODE_CONFLICT", item.action)
        assertEquals("WARNING", item.status)
        assertEquals("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", item.resultRevisionId)
        assertEquals("module_code_already_exists", item.details["reason"])
        assertTrue(preparedSql.none { it.contains("insert into ui_registry.module_revision") })
        assertTrue(preparedSql.none { it.contains("update ui_registry.module ") })
        assertTrue(preparedSql.any { it.contains("insert into ui_registry.module_sync_run_item") })
    }

    @Test
    fun `sync one creates module with sql assets from file references`() {
        val appsRoot = Files.createTempDirectory("ui-sync-apps-")
        val moduleDir = appsRoot.resolve("db-new").createDirectories()
        val resourcesDir = moduleResourcesDir(moduleDir).resolve("sql").createDirectories()
        moduleResourcesDir(moduleDir).resolve("application.yml").writeText(
            """
            app:
              mergeMode: QUOTA
              commonSqlFile: classpath:sql/common.sql
              sources:
                - name: db1
                  jdbcUrl: jdbc:postgresql://localhost/demo
                  username: ${'$'}{DB_USER}
                  password: ${'$'}{DB_PASSWORD}
                  sqlFile: classpath:sql/db1.sql
              quotas:
                - source: db1
                  percent: 100
              target:
                enabled: true
                jdbcUrl: jdbc:postgresql://localhost/target
                username: ${'$'}{TARGET_USER}
                password: ${'$'}{TARGET_PASSWORD}
                table: target_table
            """.trimIndent(),
        )
        resourcesDir.resolve("common.sql").writeText("select 1")
        resourcesDir.resolve("db1.sql").writeText("select 2")

        val preparedSql = mutableListOf<String>()
        val stringParams = mutableListOf<String?>()
        var importedDraft: RegistryModuleDraft? = null
        var importedOriginKind: String? = null
        val service = ModuleSyncService(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(
                    preparedSql = preparedSql,
                    stringParams = stringParams,
                    existingRows = emptyList(),
                )
            },
            moduleRegistryImporter = object : ModuleRegistryImporter {
                override fun createModule(
                    moduleCode: String,
                    actorId: String,
                    actorSource: String,
                    actorDisplayName: String?,
                    originKind: String,
                    draft: RegistryModuleDraft,
                ): RegistryModuleCreationResult {
                    importedOriginKind = originKind
                    importedDraft = draft
                    return RegistryModuleCreationResult(
                        moduleId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                        moduleCode = moduleCode,
                        revisionId = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                        workingCopyId = "cccccccc-cccc-cccc-cccc-cccccccccccc",
                    )
                }
            },
        )

        val result = service.syncOneFromFiles(
            moduleCode = "db-new",
            appsRoot = appsRoot,
            actorId = "kwdev",
            actorSource = "OS_LOGIN",
            actorDisplayName = "kwdev",
        )

        assertEquals("SUCCESS", result.status)
        assertEquals("CREATED", result.items.single().action)
        assertEquals("IMPORTED_FROM_FILES", importedOriginKind)
        val draft = kotlin.test.assertNotNull(importedDraft)
        assertEquals("db-new", draft.title)
        assertTrue(draft.sqlFiles["classpath:sql/common.sql"] == "select 1")
        assertTrue(draft.sqlFiles["classpath:sql/db1.sql"] == "select 2")
        assertTrue(draft.configText.contains("target_table"))
        assertTrue(preparedSql.any { it.contains("insert into ui_registry.module_sync_run_item") })
    }

    @Test
    fun `sync one uses filesystem precheck and skips unchanged module without recalculating hash`() {
        val appsRoot = Files.createTempDirectory("ui-sync-apps-")
        val moduleDir = appsRoot.resolve("db-fast-skip").createDirectories()
        val configFile = moduleResourcesDir(moduleDir).resolve("application.yml")
        configFile.writeText(
            """
            app:
              outputDir: ./out
              sources: []
            """.trimIndent(),
        )
        val modifiedAt = java.nio.file.attribute.FileTime.fromMillis(1_710_000_000_000)
        configFile.setLastModifiedTime(modifiedAt)

        val preparedSql = mutableListOf<String>()
        val service = ModuleSyncService(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(
                    preparedSql = preparedSql,
                    stringParams = mutableListOf(),
                    existingRows = listOf(
                        mapOf(
                            "module_id" to "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                            "current_revision_id" to "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                            "content_hash" to "same-content-hash",
                        ),
                    ),
                    previousSyncRows = listOf(
                        mapOf(
                            "action" to "CREATED",
                            "status" to "SUCCESS",
                            "detected_hash" to "same-content-hash",
                            "result_revision_id" to "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                            "details" to """
                                {
                                  "reason": "module_created",
                                  "filesystemFingerprint": {
                                    "trackedFiles": [
                                      {
                                        "path": "src/main/resources/application.yml",
                                        "exists": true,
                                        "lastModifiedEpochMillis": ${modifiedAt.toMillis()},
                                        "sizeBytes": ${Files.size(configFile)}
                                      },
                                      {
                                        "path": "ui-module.yml",
                                        "exists": false,
                                        "lastModifiedEpochMillis": null,
                                        "sizeBytes": null
                                      }
                                    ]
                                  }
                                }
                            """.trimIndent(),
                        ),
                    ),
                )
            },
            moduleRegistryImporter = noopImporter,
        )

        val result = service.syncOneFromFiles(
            moduleCode = "db-fast-skip",
            appsRoot = appsRoot,
            actorId = "kwdev",
            actorSource = "OS_LOGIN",
            actorDisplayName = "kwdev",
        )

        val item = result.items.single()
        assertEquals("SUCCESS", result.status)
        assertEquals("SKIPPED", item.action)
        assertEquals("fingerprint_match", item.details["reason"])
        assertEquals(true, item.details["precheckMatched"])
        assertTrue(preparedSql.any { it.contains("from ui_registry.module_sync_run_item i") })
        assertTrue(preparedSql.none { it.contains("insert into ui_registry.module_revision") })
    }

    @Test
    fun `sync one returns failed result when global import lock is busy`() {
        val appsRoot = Files.createTempDirectory("ui-sync-apps-")
        val moduleDir = appsRoot.resolve("db-busy").createDirectories()
        moduleResourcesDir(moduleDir).resolve("application.yml").writeText(
            """
            app:
              sources: []
            """.trimIndent(),
        )

        val preparedSql = mutableListOf<String>()
        val service = ModuleSyncService(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(
                    preparedSql = preparedSql,
                    stringParams = mutableListOf(),
                    existingRows = emptyList(),
                    advisoryLockAcquired = false,
                )
            },
            moduleRegistryImporter = noopImporter,
        )

        val result = service.syncOneFromFiles(
            moduleCode = "db-busy",
            appsRoot = appsRoot,
            actorId = "kwdev",
            actorSource = "OS_LOGIN",
            actorDisplayName = "kwdev",
        )

        assertEquals("FAILED", result.status)
        assertEquals("sync_lock_not_acquired", result.items.single().details["reason"])
        assertTrue(result.errorMessage!!.contains("массовый импорт"))
        assertTrue(preparedSql.any { it.contains("pg_try_advisory_lock_shared") })
        assertTrue(preparedSql.none { it.contains("from ui_registry.module m") })
    }

    @Test
    fun `sync one returns active single sync details when same module is already importing`() {
        val appsRoot = Files.createTempDirectory("ui-sync-apps-")
        val moduleDir = appsRoot.resolve("db-busy-module").createDirectories()
        moduleResourcesDir(moduleDir).resolve("application.yml").writeText(
            """
            app:
              sources: []
            """.trimIndent(),
        )

        val preparedSql = mutableListOf<String>()
        val service = ModuleSyncService(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(
                    preparedSql = preparedSql,
                    stringParams = mutableListOf(),
                    existingRows = emptyList(),
                    advisoryLockResults = ArrayDeque(listOf(true, false, true, true)),
                    runningSingleSyncRows = listOf(
                        mapOf(
                            "sync_run_id" to "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
                            "scope" to "ONE",
                            "started_at" to "2026-04-16T11:00:00Z",
                            "module_code" to "db-busy-module",
                            "started_by_actor_id" to "other-user",
                            "started_by_actor_source" to "OS_LOGIN",
                            "started_by_actor_display_name" to "other-user",
                        ),
                    ),
                )
            },
            moduleRegistryImporter = noopImporter,
        )

        val result = service.syncOneFromFiles(
            moduleCode = "db-busy-module",
            appsRoot = appsRoot,
            actorId = "kwdev",
            actorSource = "OS_LOGIN",
            actorDisplayName = "kwdev",
        )

        val item = result.items.single()
        assertEquals("FAILED", result.status)
        assertEquals("sync_lock_not_acquired", item.details["reason"])
        assertEquals("ONE", item.details["activeSyncScope"])
        assertEquals("db-busy-module", item.details["activeSyncModuleCode"])
        assertEquals("other-user", item.details["activeSyncStartedByActorId"])
        assertTrue(result.errorMessage!!.contains("уже импортируется другим пользователем"))
        assertTrue(preparedSql.any { it.contains("scope = 'ONE'") })
    }

    @Test
    fun `current sync state reports active maintenance mode when full sync is running`() {
        val preparedSql = mutableListOf<String>()
        val service = ModuleSyncService(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(
                    preparedSql = preparedSql,
                    stringParams = mutableListOf(),
                    existingRows = emptyList(),
                    runningFullSyncRows = listOf(
                        mapOf(
                            "sync_run_id" to "cccccccc-cccc-cccc-cccc-cccccccccccc",
                            "scope" to "ALL",
                            "started_at" to "2026-04-16T10:15:30Z",
                            "started_by_actor_id" to "kwdev",
                            "started_by_actor_source" to "OS_LOGIN",
                            "started_by_actor_display_name" to "kwdev",
                        ),
                    ),
                    advisoryLockResults = ArrayDeque(listOf(false)),
                )
            },
            moduleRegistryImporter = noopImporter,
        )

        val state = service.currentSyncState()

        assertTrue(state.maintenanceMode)
        val activeSync = assertNotNull(state.activeFullSync)
        assertEquals("cccccccc-cccc-cccc-cccc-cccccccccccc", activeSync.syncRunId)
        assertEquals("ALL", activeSync.scope)
        assertEquals("kwdev", activeSync.startedByActorId)
        assertTrue(preparedSql.any { it.contains("from ui_registry.module_sync_run") })
    }

    @Test
    fun `current sync state reports active single syncs when maintenance mode is inactive`() {
        val preparedSql = mutableListOf<String>()
        val service = ModuleSyncService(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(
                    preparedSql = preparedSql,
                    stringParams = mutableListOf(),
                    existingRows = emptyList(),
                    advisoryLockResults = ArrayDeque(listOf(true, true, false)),
                    runningSingleSyncRows = listOf(
                        mapOf(
                            "sync_run_id" to "ffffffff-ffff-ffff-ffff-ffffffffffff",
                            "scope" to "ONE",
                            "started_at" to "2026-04-16T12:15:30Z",
                            "module_code" to "db-demo",
                            "started_by_actor_id" to "kwdev",
                            "started_by_actor_source" to "OS_LOGIN",
                            "started_by_actor_display_name" to "kwdev",
                        ),
                    ),
                )
            },
            moduleRegistryImporter = noopImporter,
        )

        val state = service.currentSyncState()

        assertEquals(false, state.maintenanceMode)
        val activeSync = assertNotNull(state.activeSingleSync("db-demo"))
        assertEquals("ffffffff-ffff-ffff-ffff-ffffffffffff", activeSync.syncRunId)
        assertEquals("db-demo", activeSync.moduleCode)
        assertEquals("kwdev", activeSync.startedByActorDisplayName)
        assertTrue(preparedSql.any { it.contains("scope = 'ONE'") })
    }

    @Test
    fun `current sync state clears stale single sync rows when advisory lock is free`() {
        val preparedSql = mutableListOf<String>()
        val service = ModuleSyncService(
            connectionProvider = DatabaseConnectionProvider {
                fakeConnection(
                    preparedSql = preparedSql,
                    stringParams = mutableListOf(),
                    existingRows = emptyList(),
                    advisoryLockResults = ArrayDeque(listOf(true, true, true, true)),
                    runningSingleSyncRows = listOf(
                        mapOf(
                            "sync_run_id" to "12121212-1212-1212-1212-121212121212",
                            "scope" to "ONE",
                            "started_at" to "2026-04-16T12:15:30Z",
                            "module_code" to "db-demo",
                            "started_by_actor_id" to "kwdev",
                            "started_by_actor_source" to "OS_LOGIN",
                            "started_by_actor_display_name" to "kwdev",
                        ),
                    ),
                )
            },
            moduleRegistryImporter = noopImporter,
        )

        val state = service.currentSyncState()

        assertEquals(false, state.maintenanceMode)
        assertEquals(null, state.activeSingleSync("db-demo"))
        assertTrue(preparedSql.any { it.contains("update ui_registry.module_sync_run") })
    }

    private fun fakeConnection(
        preparedSql: MutableList<String>,
        stringParams: MutableList<String?>,
        existingRows: List<Map<String, String?>>,
        advisoryLockAcquired: Boolean = true,
        advisoryLockResults: ArrayDeque<Boolean>? = null,
        runningFullSyncRows: List<Map<String, String?>> = emptyList(),
        runningSingleSyncRows: List<Map<String, String?>> = emptyList(),
        previousSyncRows: List<Map<String, String?>> = emptyList(),
    ): Connection =
        Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    val sql = args?.first() as String
                    preparedSql += sql
                    fakePreparedStatement(
                        rows = when {
                            sql.contains("pg_try_advisory") || sql.contains("pg_advisory_unlock") -> {
                                val acquired = if (advisoryLockResults != null && advisoryLockResults.isNotEmpty()) {
                                    advisoryLockResults.removeFirst()
                                } else {
                                    advisoryLockAcquired
                                }
                                listOf(mapOf("acquired" to acquired.toString()))
                            }
                            sql.contains("from ui_registry.module_sync_run_item i") -> previousSyncRows
                            sql.contains("from ui_registry.module_sync_run") && sql.contains("scope = 'ONE'") -> runningSingleSyncRows
                            sql.contains("from ui_registry.module_sync_run") -> runningFullSyncRows
                            sql.contains("from ui_registry.module m") -> existingRows
                            else -> emptyList()
                        },
                        stringParams = stringParams,
                    )
                }
                "setAutoCommit", "commit", "rollback", "close" -> null
                "getAutoCommit" -> false
                else -> defaultReturnValue(method.returnType)
            }
        } as Connection

    private fun fakePreparedStatement(
        rows: List<Map<String, String?>>,
        stringParams: MutableList<String?>,
    ): PreparedStatement =
        Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { _, method, args ->
            when (method.name) {
                "executeQuery" -> fakeResultSet(rows)
                "executeUpdate" -> 1
                "setString" -> {
                    stringParams += args?.get(1) as String?
                    null
                }
                "setBoolean", "setInt", "setLong", "setObject", "close" -> null
                else -> defaultReturnValue(method.returnType)
            }
        } as PreparedStatement

    private fun fakeResultSet(rows: List<Map<String, String?>>): ResultSet {
        var index = -1
        return Proxy.newProxyInstance(
            ResultSet::class.java.classLoader,
            arrayOf(ResultSet::class.java),
        ) { _, method, args ->
            when (method.name) {
                "next" -> {
                    index += 1
                    index < rows.size
                }
                "getString" -> rows[index][args?.first() as String]
                "getBoolean" -> rows[index][args?.first() as String].toBoolean()
                "getTimestamp" -> rows[index][args?.first() as String]?.let { Timestamp.from(java.time.Instant.parse(it)) }
                "close" -> null
                else -> defaultReturnValue(method.returnType)
            }
        } as ResultSet
    }

    private fun defaultReturnValue(returnType: Class<*>): Any? = when (returnType) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        Void.TYPE -> null
        else -> null
    }
}
