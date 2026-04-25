package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.sqlconsole.RawShardConnectionCheckResult
import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.ShardConnectionChecker
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.ShardSqlObjectColumnLoader
import com.sbrf.lt.datapool.sqlconsole.ShardSqlObjectInspector
import com.sbrf.lt.datapool.sqlconsole.ShardSqlObjectSearchResult
import com.sbrf.lt.datapool.sqlconsole.ShardSqlObjectSearcher
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObject
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectColumn
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectConstraint
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectIndex
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectInspector
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectType
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionCancelledException
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceGroupConfig
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigPersistenceService
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import com.sbrf.lt.platform.ui.run.RunManager
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExecutionHistoryService
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManager
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqlConsoleServerTest {

    @Test
    fun `serves sql console sync routes and workspace state`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val storageDir = Files.createTempDirectory("ui-server-state-")
        val uiConfig = UiAppConfig(
            storageDir = storageDir.toString(),
            sqlConsole = SqlConsoleConfig(
                queryTimeoutSec = 30,
                groups = listOf(SqlConsoleSourceGroupConfig("dev", listOf("shard1"))),
                sourceCatalog = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user", "pwd")),
            ),
        )
        val runManager = RunManager(moduleRegistry = registry, uiConfig = uiConfig)
        val sqlConsoleService = SqlConsoleService(
            config = uiConfig.sqlConsole,
            executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                assertEquals("SELECT", statement.leadingKeyword)
                RawShardExecutionResult(
                    shardName = shard.name,
                    status = "SUCCESS",
                    columns = listOf("id"),
                    rows = listOf(mapOf("id" to "1")),
                    truncated = false,
                )
            },
            connectionChecker = ShardConnectionChecker { shard, _ ->
                RawShardConnectionCheckResult(
                    shardName = shard.name,
                    status = "SUCCESS",
                    message = "Подключение установлено.",
                )
            },
            objectSearcher = ShardSqlObjectSearcher { shard, rawQuery, _ ->
                assertEquals("offer", rawQuery)
                ShardSqlObjectSearchResult(
                    objects = listOf(
                        SqlConsoleDatabaseObject(
                            schemaName = "public",
                            objectName = "${shard.name}_offer",
                            objectType = SqlConsoleDatabaseObjectType.TABLE,
                        ),
                    ),
                )
            },
            objectInspector = ShardSqlObjectInspector { shard, schemaName, objectName, objectType ->
                assertEquals("shard1", shard.name)
                assertEquals("public", schemaName)
                assertEquals("shard1_offer", objectName)
                assertEquals(SqlConsoleDatabaseObjectType.TABLE, objectType)
                SqlConsoleDatabaseObjectInspector(
                    schemaName = schemaName,
                    objectName = objectName,
                    objectType = objectType,
                    definition = "create table public.shard1_offer (id bigint not null);",
                    columns = listOf(
                        SqlConsoleDatabaseObjectColumn(
                            name = "id",
                            type = "bigint",
                            nullable = false,
                        ),
                    ),
                    indexes = listOf(
                        SqlConsoleDatabaseObjectIndex(
                            name = "shard1_offer_idx",
                            tableName = objectName,
                            columns = listOf("id"),
                            unique = true,
                            definition = "create unique index shard1_offer_idx on public.shard1_offer (id);",
                        ),
                    ),
                    constraints = listOf(
                        SqlConsoleDatabaseObjectConstraint(
                            name = "shard1_offer_pkey",
                            type = "PRIMARY KEY",
                            columns = listOf("id"),
                            definition = "primary key (id)",
                        ),
                    ),
                )
            },
            objectColumnLoader = ShardSqlObjectColumnLoader { shard, schemaName, objectName, objectType ->
                assertEquals("shard1", shard.name)
                assertEquals("public", schemaName)
                assertEquals("shard1_offer", objectName)
                assertEquals(SqlConsoleDatabaseObjectType.TABLE, objectType)
                listOf(
                    SqlConsoleDatabaseObjectColumn(
                        name = "id",
                        type = "bigint",
                        nullable = false,
                    ),
                    SqlConsoleDatabaseObjectColumn(
                        name = "created_at",
                        type = "timestamp",
                        nullable = true,
                    ),
                )
            },
        )
        var savedMaxRows: Int? = null
        var savedTimeout: Int? = null
        val uiConfigPersistenceService = object : UiConfigPersistenceService() {
            override fun updateSqlConsoleSettings(maxRowsPerShard: Int, queryTimeoutSec: Int?): UiAppConfig {
                savedMaxRows = maxRowsPerShard
                savedTimeout = queryTimeoutSec
                return uiConfig.copy(
                    sqlConsole = uiConfig.sqlConsole.copy(
                        maxRowsPerShard = maxRowsPerShard,
                        queryTimeoutSec = queryTimeoutSec,
                    ),
                )
            }
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                moduleRegistry = registry,
                runManager = runManager,
                sqlConsoleService = sqlConsoleService,
                uiConfigPersistenceService = uiConfigPersistenceService,
            )
        }

        val noRedirectClient = createClient { followRedirects = false }

        val sqlConsoleRedirect = noRedirectClient.get("/sql-console")
        assertEquals(HttpStatusCode.Found, sqlConsoleRedirect.status)
        assertEquals(
            "/static/compose-app/index.html?screen=sql-console",
            sqlConsoleRedirect.headers[HttpHeaders.Location],
        )

        val sqlConsoleObjectsRedirect = noRedirectClient.get("/sql-console-objects?workspaceId=workspace-a&type=TABLE")
        assertEquals(HttpStatusCode.Found, sqlConsoleObjectsRedirect.status)
        assertEquals(
            "/static/compose-app/index.html?screen=sql-console-objects&workspaceId=workspace-a&type=TABLE",
            sqlConsoleObjectsRedirect.headers[HttpHeaders.Location],
        )

        val removedStaticCompatibility = noRedirectClient.get("/static/compose-spike/index.html?screen=sql-console")
        assertEquals(HttpStatusCode.NotFound, removedStaticCompatibility.status)

        val removedComposeSqlConsole = noRedirectClient.get("/compose-sql-console")
        assertEquals(HttpStatusCode.NotFound, removedComposeSqlConsole.status)

        val removedComposeSqlConsoleObjects = noRedirectClient.get("/compose-sql-console-objects?workspaceId=workspace-a&query=offer")
        assertEquals(HttpStatusCode.NotFound, removedComposeSqlConsoleObjects.status)

        val info = client.get("/api/sql-console/info").bodyAsText()
        assertTrue(info.contains("\"sourceCatalog\":[{\"name\":\"shard1\"}]"))
        assertTrue(info.contains("\"groups\":[{\"name\":\"dev\",\"sources\":[\"shard1\"],\"synthetic\":false}]"))
        assertTrue(info.contains("\"queryTimeoutSec\":30"))
        assertTrue(info.contains("\"maxRowsPerShard\":200"))

        val state = client.get("/api/sql-console/state?workspaceId=workspace-a").bodyAsText()
        assertTrue(state.contains("\"draftSql\":\"select 1 as check_value\""))
        assertTrue(state.contains("\"pageSize\":50"))

        val savedState = client.post("/api/sql-console/state?workspaceId=workspace-a") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "draftSql":"select * from demo",
                  "recentQueries":["select * from demo"],
                  "selectedGroupNames":["dev"],
                  "favoriteObjects":[
                    {
                      "sourceName":"shard1",
                      "schemaName":"public",
                      "objectName":"offer",
                      "objectType":"TABLE"
                    }
                  ],
                  "selectedSourceNames":["shard1"],
                  "pageSize":100
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, savedState.status)
        val savedStateBody = savedState.bodyAsText()
        assertTrue(savedStateBody.contains("\"draftSql\":\"select * from demo\""))
        assertTrue(savedStateBody.contains("\"selectedGroupNames\":[\"dev\"]"))
        assertTrue(savedStateBody.contains("\"pageSize\":100"))
        assertTrue(savedStateBody.contains("\"favoriteObjects\":[{\"sourceName\":\"shard1\""))

        val secondWorkspaceState = client.get("/api/sql-console/state?workspaceId=workspace-b").bodyAsText()
        assertTrue(secondWorkspaceState.contains("\"draftSql\":\"select 1 as check_value\""))
        assertTrue(secondWorkspaceState.contains("\"selectedSourceNames\":[]"))

        val savedSecondWorkspace = client.post("/api/sql-console/state?workspaceId=workspace-b") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "draftSql":"select * from second_workspace",
                  "selectedGroupNames":[],
                  "selectedSourceNames":["shard1"],
                  "pageSize":25
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, savedSecondWorkspace.status)

        val restoredFirstWorkspace = client.get("/api/sql-console/state?workspaceId=workspace-a").bodyAsText()
        assertTrue(restoredFirstWorkspace.contains("\"draftSql\":\"select * from demo\""))
        assertTrue(restoredFirstWorkspace.contains("\"selectedGroupNames\":[\"dev\"]"))
        val restoredSecondWorkspace = client.get("/api/sql-console/state?workspaceId=workspace-b").bodyAsText()
        assertTrue(restoredSecondWorkspace.contains("\"draftSql\":\"select * from second_workspace\""))

        val settings = client.post("/api/sql-console/settings") {
            contentType(ContentType.Application.Json)
            setBody("""{"maxRowsPerShard":350,"queryTimeoutSec":75}""")
        }
        assertEquals(HttpStatusCode.OK, settings.status)
        val settingsBody = settings.bodyAsText()
        assertTrue(settingsBody.contains("\"maxRowsPerShard\":350"))
        assertTrue(settingsBody.contains("\"queryTimeoutSec\":75"))
        assertEquals(350, savedMaxRows)
        assertEquals(75, savedTimeout)

        val connections = client.post("/api/sql-console/connections/check").bodyAsText()
        assertTrue(connections.contains("\"configured\":true"))
        assertTrue(connections.contains("\"sourceName\":\"shard1\""))
        assertTrue(connections.contains("\"status\":\"SUCCESS\""))

        val objects = client.post("/api/sql-console/objects/search") {
            contentType(ContentType.Application.Json)
            setBody("""{"query":"offer","selectedSourceNames":["shard1"],"maxObjectsPerSource":30}""")
        }.bodyAsText()
        assertTrue(objects.contains("\"query\":\"offer\""))
        assertTrue(objects.contains("\"sourceName\":\"shard1\""))
        assertTrue(objects.contains("\"objectName\":\"shard1_offer\""))
        assertFalse(objects.contains("\"indexNames\""))

        val inspector = client.post("/api/sql-console/objects/inspect") {
            contentType(ContentType.Application.Json)
            setBody("""{"sourceName":"shard1","schemaName":"public","objectName":"shard1_offer","objectType":"TABLE"}""")
        }.bodyAsText()
        assertTrue(inspector.contains("\"sourceName\":\"shard1\""))
        assertTrue(inspector.contains("\"objectName\":\"shard1_offer\""))
        assertTrue(inspector.contains("\"definition\":\"create table public.shard1_offer (id bigint not null);\""))
        assertTrue(inspector.contains("\"constraints\":["))

        val objectColumns = client.post("/api/sql-console/objects/columns") {
            contentType(ContentType.Application.Json)
            setBody("""{"schemaName":"public","objectName":"shard1_offer","objectType":"TABLE","selectedSourceNames":["shard1"]}""")
        }.bodyAsText()
        assertTrue(objectColumns.contains("\"schemaName\":\"public\""))
        assertTrue(objectColumns.contains("\"objectName\":\"shard1_offer\""))
        assertTrue(objectColumns.contains("\"objectType\":\"TABLE\""))
        assertTrue(objectColumns.contains("\"sourceName\":\"shard1\""))
        assertTrue(objectColumns.contains("\"name\":\"created_at\""))
        assertFalse(objectColumns.contains("\"definition\""))

        val queryResponse = client.post("/api/sql-console/query") {
            contentType(ContentType.Application.Json)
            setBody("""{"sql":"select 1 as id","selectedSourceNames":["shard1"],"ownerSessionId":"tab-sync"}""")
        }
        assertEquals(HttpStatusCode.OK, queryResponse.status)
        val queryBody = queryResponse.bodyAsText()
        assertTrue(queryBody.contains("\"statementType\":\"RESULT_SET\""))
        assertTrue(queryBody.contains("\"statementKeyword\":\"SELECT\""))
        assertTrue(queryBody.contains("\"shardName\":\"shard1\""))
        assertTrue(queryBody.contains("\"rows\":[{\"id\":\"1\"}]"))
        assertTrue(queryBody.contains("\"startedAt\":"))
        assertTrue(queryBody.contains("\"durationMillis\":"))

        val exportCsvResponse = client.post("/api/sql-console/export/source-csv") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "result": {
                    "sql": "select 1 as id",
                    "statementType": "RESULT_SET",
                    "statementKeyword": "SELECT",
                    "maxRowsPerShard": 200,
                    "shardResults": [
                      {
                        "shardName": "shard1",
                        "status": "SUCCESS",
                        "rows": [{"id": "1"}],
                        "rowCount": 1,
                        "columns": ["id"],
                        "truncated": false
                      }
                    ]
                  },
                  "shardName": "shard1"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, exportCsvResponse.status)
        assertTrue(exportCsvResponse.headers[HttpHeaders.ContentDisposition]?.contains("shard1.csv") == true)
        assertEquals("id\n1\n", exportCsvResponse.bodyAsText())

        val exportZipResponse = client.post("/api/sql-console/export/all-zip") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "result": {
                    "sql": "select 1 as id",
                    "statementType": "RESULT_SET",
                    "statementKeyword": "SELECT",
                    "maxRowsPerShard": 200,
                    "shardResults": [
                      {
                        "shardName": "shard1",
                        "status": "SUCCESS",
                        "rows": [{"id": "1"}],
                        "rowCount": 1,
                        "columns": ["id"],
                        "truncated": false
                      }
                    ]
                  }
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, exportZipResponse.status)
        assertTrue(exportZipResponse.headers[HttpHeaders.ContentDisposition]?.contains("sql-console-results.zip") == true)
        val zipBytes: ByteArray = exportZipResponse.body()
        assertTrue(zipBytes.isNotEmpty())
    }

    @Test
    fun `supports async sql console lifecycle and service routes`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val storageDir = Files.createTempDirectory("ui-server-state-")
        val uiConfig = UiAppConfig(
            storageDir = storageDir.toString(),
            sqlConsole = SqlConsoleConfig(
                queryTimeoutSec = 30,
                sourceCatalog = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user", "pwd")),
            ),
        )
        val runManager = RunManager(moduleRegistry = registry, uiConfig = uiConfig)
        val sqlConsoleService = SqlConsoleService(
            config = uiConfig.sqlConsole,
            executor = ShardSqlExecutor { shard, statement, _, _, _, control ->
                assertEquals("DELETE", statement.leadingKeyword)
                repeat(30) {
                    if (control.isCancelled()) {
                        throw SqlConsoleExecutionCancelledException("Запрос отменен пользователем.")
                    }
                    Thread.sleep(10)
                }
                RawShardExecutionResult(
                    shardName = shard.name,
                    status = "SUCCESS",
                    affectedRows = 3,
                    message = "DELETE выполнен успешно.",
                )
            },
        )
        val historyService = SqlConsoleExecutionHistoryService(storageDir)
        val queryManager = SqlConsoleQueryManager(
            sqlConsoleService = sqlConsoleService,
            executionHistoryService = historyService,
        )
        application {
            uiModule(
                moduleRegistry = registry,
                runManager = runManager,
                sqlConsoleService = sqlConsoleService,
                sqlConsoleQueryManager = queryManager,
                sqlConsoleExecutionHistoryService = historyService,
            )
        }

        val stateBody = client.get("/api/state").bodyAsText()
        assertTrue(stateBody.contains("\"history\":[]"))

        val credentialsBody = client.get("/api/credentials").bodyAsText()
        assertTrue(credentialsBody.contains("\"mode\":\"NONE\""))

        val started = client.post("/api/sql-console/query/start") {
            contentType(ContentType.Application.Json)
            setBody("""{"sql":"delete from demo","selectedSourceNames":["shard1"],"workspaceId":"workspace-a","ownerSessionId":"tab-1"}""")
        }
        assertEquals(HttpStatusCode.OK, started.status)
        val startedBody = started.bodyAsText()
        assertTrue(startedBody.contains("\"status\":\"RUNNING\""))
        val executionId = startedBody.jsonStringField("id")
        assertNotNull(executionId)
        val ownerToken = startedBody.jsonStringField("ownerToken")
        assertNotNull(ownerToken)

        val running = client.get("/api/sql-console/query/$executionId").bodyAsText()
        assertTrue(running.contains("\"status\":\"RUNNING\""))

        val duplicateStart = client.post("/api/sql-console/query/start") {
            contentType(ContentType.Application.Json)
            setBody("""{"sql":"delete from demo_again","selectedSourceNames":["shard1"],"workspaceId":"workspace-b","ownerSessionId":"tab-2"}""")
        }
        assertEquals(HttpStatusCode.OK, duplicateStart.status)
        val duplicateExecutionId = duplicateStart.bodyAsText().jsonStringField("id")
        assertNotNull(duplicateExecutionId)
        val duplicateRunning = client.get("/api/sql-console/query/$duplicateExecutionId")
        assertEquals(HttpStatusCode.OK, duplicateRunning.status)

        val heartbeat = client.post("/api/sql-console/query/$executionId/heartbeat") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-1","ownerToken":"$ownerToken"}""")
        }
        assertEquals(HttpStatusCode.OK, heartbeat.status)
        val rotatedOwnerToken = heartbeat.bodyAsText().jsonStringField("ownerToken")
        assertNotNull(rotatedOwnerToken)
        assertNotEquals(ownerToken, rotatedOwnerToken)

        val staleCancel = client.post("/api/sql-console/query/$executionId/cancel") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-1","ownerToken":"$ownerToken"}""")
        }
        assertEquals(HttpStatusCode.Conflict, staleCancel.status)
        assertTrue(staleCancel.bodyAsText().contains("не принадлежит"))

        val released = client.post("/api/sql-console/query/$executionId/release") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-1","ownerToken":"$rotatedOwnerToken"}""")
        }
        assertEquals(HttpStatusCode.OK, released.status)

        val releasedCancel = client.post("/api/sql-console/query/$executionId/cancel") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-1","ownerToken":"$rotatedOwnerToken"}""")
        }
        assertEquals(HttpStatusCode.Conflict, releasedCancel.status)
        assertTrue(releasedCancel.bodyAsText().contains("control-path"))

        val recoveredHeartbeat = client.post("/api/sql-console/query/$executionId/heartbeat") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-1","ownerToken":"$rotatedOwnerToken"}""")
        }
        assertEquals(HttpStatusCode.OK, recoveredHeartbeat.status)
        val recoveredOwnerToken = recoveredHeartbeat.bodyAsText().jsonStringField("ownerToken")
        assertNotNull(recoveredOwnerToken)
        assertNotEquals(rotatedOwnerToken, recoveredOwnerToken)

        val cancelled = client.post("/api/sql-console/query/$executionId/cancel") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-1","ownerToken":"$recoveredOwnerToken"}""")
        }.bodyAsText()
        assertTrue(cancelled.contains("\"cancelRequested\":true"))

        repeat(20) {
            val polled = client.get("/api/sql-console/query/$executionId").bodyAsText()
            if (polled.contains("\"status\":\"CANCELLED\"")) {
                assertTrue(polled.contains("Запрос отменен пользователем"))
                return@testApplication
            }
            Thread.sleep(50)
        }
        error("async SQL execution was not cancelled in time")
    }

    @Test
    fun `async sql final route response clears control path metadata and rejects release`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val storageDir = Files.createTempDirectory("ui-server-state-")
        val uiConfig = UiAppConfig(
            storageDir = storageDir.toString(),
            sqlConsole = SqlConsoleConfig(
                queryTimeoutSec = 30,
                sourceCatalog = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user", "pwd")),
            ),
        )
        val runManager = RunManager(moduleRegistry = registry, uiConfig = uiConfig)
        val sqlConsoleService = SqlConsoleService(
            config = uiConfig.sqlConsole,
            executor = ShardSqlExecutor { shard, statement, _, _, _, _ ->
                assertEquals("SELECT", statement.leadingKeyword)
                RawShardExecutionResult(
                    shardName = shard.name,
                    status = "SUCCESS",
                    columns = listOf("id"),
                    rows = listOf(mapOf("id" to "1")),
                )
            },
        )
        val queryManager = SqlConsoleQueryManager(sqlConsoleService = sqlConsoleService)
        application {
            uiModule(
                moduleRegistry = registry,
                runManager = runManager,
                sqlConsoleService = sqlConsoleService,
                sqlConsoleQueryManager = queryManager,
            )
        }

        val started = client.post("/api/sql-console/query/start") {
            contentType(ContentType.Application.Json)
            setBody("""{"sql":"select 1 as id","selectedSourceNames":["shard1"],"workspaceId":"workspace-a","ownerSessionId":"tab-1"}""")
        }
        assertEquals(HttpStatusCode.OK, started.status)
        val startedBody = started.bodyAsText()
        val executionId = startedBody.jsonStringField("id")
        val ownerToken = startedBody.jsonStringField("ownerToken")
        assertNotNull(executionId)
        assertNotNull(ownerToken)

        repeat(20) {
            val polled = client.get("/api/sql-console/query/$executionId").bodyAsText()
            if (polled.contains(""""status":"SUCCESS"""")) {
                assertTrue(polled.contains(""""transactionState":"NONE""""), polled)
                assertTrue(polled.jsonFieldIsNullOrMissing("ownerToken"), polled)
                assertTrue(polled.jsonFieldIsNullOrMissing("ownerLeaseExpiresAt"), polled)
                assertTrue(polled.jsonFieldIsNullOrMissing("pendingCommitExpiresAt"), polled)
                val released = client.post("/api/sql-console/query/$executionId/release") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"ownerSessionId":"tab-1","ownerToken":"$ownerToken"}""")
                }
                assertEquals(HttpStatusCode.Conflict, released.status)
                assertTrue(released.bodyAsText().contains("control-path"))

                val heartbeat = client.post("/api/sql-console/query/$executionId/heartbeat") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"ownerSessionId":"tab-1","ownerToken":"$ownerToken"}""")
                }
                assertEquals(HttpStatusCode.Conflict, heartbeat.status)
                assertTrue(heartbeat.bodyAsText().contains("больше не требует heartbeat"))

                val cancelled = client.post("/api/sql-console/query/$executionId/cancel") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"ownerSessionId":"tab-1","ownerToken":"$ownerToken"}""")
                }
                assertEquals(HttpStatusCode.Conflict, cancelled.status)
                assertTrue(cancelled.bodyAsText().contains("уже завершен"))
                return@testApplication
            }
            Thread.sleep(25)
        }
        error("async SQL execution did not complete in time")
    }

    @Test
    fun `rejects second manual transaction route when another workspace already has pending commit`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val storageDir = Files.createTempDirectory("ui-server-state-")
        val uiConfig = UiAppConfig(
            storageDir = storageDir.toString(),
            sqlConsole = SqlConsoleConfig(
                queryTimeoutSec = 30,
                sourceCatalog = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user", "pwd")),
            ),
        )
        val runManager = RunManager(moduleRegistry = registry, uiConfig = uiConfig)
        val sqlConsoleService = SqlConsoleService(
            config = uiConfig.sqlConsole,
            executor = object : ShardSqlExecutor, com.sbrf.lt.datapool.sqlconsole.ShardSqlTransactionalExecutor {
                override fun execute(
                    shard: com.sbrf.lt.datapool.sqlconsole.ResolvedSqlConsoleShardConfig,
                    statement: com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatement,
                    fetchSize: Int,
                    maxRows: Int,
                    queryTimeoutSec: Int?,
                    executionControl: com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl,
                ): RawShardExecutionResult =
                    RawShardExecutionResult(
                        shardName = shard.name,
                        status = "SUCCESS",
                        columns = listOf("id"),
                        rows = listOf(mapOf("id" to "1")),
                    )

                override fun executeScriptInTransaction(
                    shard: com.sbrf.lt.datapool.sqlconsole.ResolvedSqlConsoleShardConfig,
                    statements: List<com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatement>,
                    fetchSize: Int,
                    maxRows: Int,
                    queryTimeoutSec: Int?,
                    executionPolicy: com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy,
                    executionControl: com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl,
                ): com.sbrf.lt.datapool.sqlconsole.TransactionalShardScriptExecution =
                    com.sbrf.lt.datapool.sqlconsole.TransactionalShardScriptExecution(
                        results = statements.map {
                            RawShardExecutionResult(
                                shardName = shard.name,
                                status = "SUCCESS",
                                affectedRows = 1,
                                message = "ok",
                            )
                        },
                        pendingTransaction = object : com.sbrf.lt.datapool.sqlconsole.PendingShardTransaction {
                            override val shardName: String = shard.name

                            override fun commit() = Unit

                            override fun rollback() = Unit
                        },
                    )
            },
        )
        val historyService = SqlConsoleExecutionHistoryService(storageDir)
        val queryManager = SqlConsoleQueryManager(
            sqlConsoleService = sqlConsoleService,
            executionHistoryService = historyService,
        )
        application {
            uiModule(
                moduleRegistry = registry,
                runManager = runManager,
                sqlConsoleService = sqlConsoleService,
                sqlConsoleQueryManager = queryManager,
                sqlConsoleExecutionHistoryService = historyService,
            )
        }

        val started = client.post("/api/sql-console/query/start") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"sql":"update demo set flag = true","selectedSourceNames":["shard1"],"workspaceId":"workspace-a","ownerSessionId":"tab-1","transactionMode":"TRANSACTION_PER_SHARD"}""",
            )
        }
        assertEquals(HttpStatusCode.OK, started.status)
        val startedBody = started.bodyAsText()
        val executionId = startedBody.jsonStringField("id")
        assertNotNull(executionId)
        val ownerToken = startedBody.jsonStringField("ownerToken")
        assertNotNull(ownerToken)

        repeat(20) {
            val polled = client.get("/api/sql-console/query/$executionId").bodyAsText()
            if (polled.contains(""""transactionState":"PENDING_COMMIT"""")) {
                val historyBody = client.get("/api/sql-console/history?workspaceId=workspace-a").bodyAsText()
                assertTrue(historyBody.contains(""""executionId":"$executionId""""))
                assertTrue(historyBody.contains(""""transactionState":"PENDING_COMMIT""""))

                val duplicateStart = client.post("/api/sql-console/query/start") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"sql":"update demo_again set flag = true","selectedSourceNames":["shard1"],"workspaceId":"workspace-b","ownerSessionId":"tab-2","transactionMode":"TRANSACTION_PER_SHARD"}""",
                    )
                }
                assertEquals(HttpStatusCode.Conflict, duplicateStart.status)
                assertTrue(
                    duplicateStart.bodyAsText().contains(
                        "В другой вкладке SQL-консоли есть незавершенная транзакция. Сначала выполните Commit или Rollback в той вкладке. Пока транзакция не завершена, запуск новой ручной транзакции недоступен.",
                    ),
                )

                val rolledBack = client.post("/api/sql-console/query/$executionId/rollback") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"ownerSessionId":"tab-1","ownerToken":"$ownerToken"}""")
                }
                assertEquals(HttpStatusCode.OK, rolledBack.status)
                return@testApplication
            }
            Thread.sleep(25)
        }
        error("manual SQL execution did not reach pending commit in time")
    }

    @Test
    fun `manual transaction final route responses clear control path metadata`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val storageDir = Files.createTempDirectory("ui-server-state-")
        val uiConfig = UiAppConfig(
            storageDir = storageDir.toString(),
            sqlConsole = SqlConsoleConfig(
                queryTimeoutSec = 30,
                sourceCatalog = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user", "pwd")),
            ),
        )
        val runManager = RunManager(moduleRegistry = registry, uiConfig = uiConfig)
        val sqlConsoleService = manualTransactionSqlConsoleService(uiConfig.sqlConsole)
        val queryManager = SqlConsoleQueryManager(sqlConsoleService = sqlConsoleService)
        application {
            uiModule(
                moduleRegistry = registry,
                runManager = runManager,
                sqlConsoleService = sqlConsoleService,
                sqlConsoleQueryManager = queryManager,
            )
        }

        suspend fun startPendingManualTransaction(
            workspaceId: String,
            ownerSessionId: String,
        ): Pair<String, String> {
            val started = client.post("/api/sql-console/query/start") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"sql":"update demo set flag = true","selectedSourceNames":["shard1"],"workspaceId":"$workspaceId","ownerSessionId":"$ownerSessionId","transactionMode":"TRANSACTION_PER_SHARD"}""",
                )
            }
            assertEquals(HttpStatusCode.OK, started.status)
            val startedBody = started.bodyAsText()
            val executionId = startedBody.jsonStringField("id")
            assertNotNull(executionId)
            val ownerToken = startedBody.jsonStringField("ownerToken")
            assertNotNull(ownerToken)

            repeat(20) {
                val polled = client.get("/api/sql-console/query/$executionId").bodyAsText()
                if (polled.contains(""""transactionState":"PENDING_COMMIT"""")) {
                    return executionId to ownerToken
                }
                Thread.sleep(25)
            }
            error("manual SQL execution did not reach pending commit in time")
        }

        val (commitExecutionId, commitOwnerToken) = startPendingManualTransaction("workspace-commit", "tab-commit")
        val committed = client.post("/api/sql-console/query/$commitExecutionId/commit") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-commit","ownerToken":"$commitOwnerToken"}""")
        }
        assertEquals(HttpStatusCode.OK, committed.status)
        assertFinalRouteControlPathCleared(committed.bodyAsText(), "COMMITTED")
        val commitRelease = client.post("/api/sql-console/query/$commitExecutionId/release") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-commit","ownerToken":"$commitOwnerToken"}""")
        }
        assertEquals(HttpStatusCode.Conflict, commitRelease.status)
        assertTrue(commitRelease.bodyAsText().contains("control-path"))
        val secondCommit = client.post("/api/sql-console/query/$commitExecutionId/commit") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-commit","ownerToken":"$commitOwnerToken"}""")
        }
        assertEquals(HttpStatusCode.Conflict, secondCommit.status)
        assertTrue(secondCommit.bodyAsText().contains("нет незавершенной транзакции"))

        val (rollbackExecutionId, rollbackOwnerToken) = startPendingManualTransaction("workspace-rollback", "tab-rollback")
        val rolledBack = client.post("/api/sql-console/query/$rollbackExecutionId/rollback") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-rollback","ownerToken":"$rollbackOwnerToken"}""")
        }
        assertEquals(HttpStatusCode.OK, rolledBack.status)
        assertFinalRouteControlPathCleared(rolledBack.bodyAsText(), "ROLLED_BACK")
        val rollbackRelease = client.post("/api/sql-console/query/$rollbackExecutionId/release") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-rollback","ownerToken":"$rollbackOwnerToken"}""")
        }
        assertEquals(HttpStatusCode.Conflict, rollbackRelease.status)
        assertTrue(rollbackRelease.bodyAsText().contains("control-path"))
        val secondRollback = client.post("/api/sql-console/query/$rollbackExecutionId/rollback") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-rollback","ownerToken":"$rollbackOwnerToken"}""")
        }
        assertEquals(HttpStatusCode.Conflict, secondRollback.status)
        assertTrue(secondRollback.bodyAsText().contains("нет незавершенной транзакции"))
    }

    @Test
    fun `expiring one workspace release does not remove cancel path from another workspace`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val storageDir = Files.createTempDirectory("ui-server-state-")
        val uiConfig = UiAppConfig(
            storageDir = storageDir.toString(),
            sqlConsole = SqlConsoleConfig(
                queryTimeoutSec = 30,
                sourceCatalog = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user", "pwd")),
            ),
        )
        val runManager = RunManager(moduleRegistry = registry, uiConfig = uiConfig)
        val releaseExecution = CountDownLatch(1)
        val sqlConsoleService = SqlConsoleService(
            config = uiConfig.sqlConsole,
            executor = ShardSqlExecutor { shard, _, _, _, _, control ->
                repeat(100) {
                    if (control.isCancelled()) {
                        throw SqlConsoleExecutionCancelledException("Запрос отменен пользователем.")
                    }
                    if (releaseExecution.count == 0L) {
                        return@ShardSqlExecutor RawShardExecutionResult(
                            shardName = shard.name,
                            status = "SUCCESS",
                            columns = listOf("id"),
                            rows = listOf(mapOf("id" to "1")),
                        )
                    }
                    Thread.sleep(5)
                }
                releaseExecution.await()
                RawShardExecutionResult(
                    shardName = shard.name,
                    status = "SUCCESS",
                    columns = listOf("id"),
                    rows = listOf(mapOf("id" to "1")),
                )
            },
        )
        val queryManager = SqlConsoleQueryManager(
            sqlConsoleService = sqlConsoleService,
            ownerLeaseDuration = Duration.ofSeconds(30),
            ownerReleaseRecoveryWindow = Duration.ofMillis(100),
        )
        application {
            uiModule(
                moduleRegistry = registry,
                runManager = runManager,
                sqlConsoleService = sqlConsoleService,
                sqlConsoleQueryManager = queryManager,
            )
        }

        val startedFirst = client.post("/api/sql-console/query/start") {
            contentType(ContentType.Application.Json)
            setBody("""{"sql":"select 1 as first_value","selectedSourceNames":["shard1"],"workspaceId":"workspace-a","ownerSessionId":"tab-1"}""")
        }
        assertEquals(HttpStatusCode.OK, startedFirst.status)
        val startedFirstBody = startedFirst.bodyAsText()
        val firstExecutionId = startedFirstBody.jsonStringField("id")
        assertNotNull(firstExecutionId)
        val firstOwnerToken = startedFirstBody.jsonStringField("ownerToken")
        assertNotNull(firstOwnerToken)

        val startedSecond = client.post("/api/sql-console/query/start") {
            contentType(ContentType.Application.Json)
            setBody("""{"sql":"select 1 as second_value","selectedSourceNames":["shard1"],"workspaceId":"workspace-b","ownerSessionId":"tab-2"}""")
        }
        assertEquals(HttpStatusCode.OK, startedSecond.status)
        val startedSecondBody = startedSecond.bodyAsText()
        val secondExecutionId = startedSecondBody.jsonStringField("id")
        assertNotNull(secondExecutionId)
        val secondOwnerToken = startedSecondBody.jsonStringField("ownerToken")
        assertNotNull(secondOwnerToken)

        val released = client.post("/api/sql-console/query/$firstExecutionId/release") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-1","ownerToken":"$firstOwnerToken"}""")
        }
        assertEquals(HttpStatusCode.OK, released.status)

        Thread.sleep(150)

        val expiredHeartbeat = client.post("/api/sql-console/query/$firstExecutionId/heartbeat") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-1","ownerToken":"$firstOwnerToken"}""")
        }
        assertEquals(HttpStatusCode.Conflict, expiredHeartbeat.status)
        assertTrue(expiredHeartbeat.bodyAsText().contains("потеряла владельца"))

        val cancelledSecond = client.post("/api/sql-console/query/$secondExecutionId/cancel") {
            contentType(ContentType.Application.Json)
            setBody("""{"ownerSessionId":"tab-2","ownerToken":"$secondOwnerToken"}""")
        }
        assertEquals(HttpStatusCode.OK, cancelledSecond.status)
        assertTrue(cancelledSecond.bodyAsText().contains(""""cancelRequested":true"""))

        releaseExecution.countDown()

        repeat(20) {
            val polled = client.get("/api/sql-console/query/$secondExecutionId").bodyAsText()
            if (polled.contains("\"status\":\"CANCELLED\"")) {
                assertTrue(polled.contains("Запрос отменен пользователем"))
                return@testApplication
            }
            Thread.sleep(25)
        }
        error("second workspace SQL execution was not cancelled in time")
    }

    @Test
    fun `sql console export routes validate shard selection`() = testApplication {
        application {
            uiModule()
        }

        val invalidExportResponse = client.post("/api/sql-console/export/source-csv") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "result": {
                    "sql": "select 1",
                    "statementType": "RESULT_SET",
                    "statementKeyword": "SELECT",
                    "maxRowsPerShard": 200,
                    "shardResults": []
                  },
                  "shardName": "   "
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidExportResponse.status)
        assertTrue(invalidExportResponse.bodyAsText().contains("shardName"))

        val missingShardExportResponse = client.post("/api/sql-console/export/source-csv") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "result": {
                    "sql": "select 1",
                    "statementType": "RESULT_SET",
                    "statementKeyword": "SELECT",
                    "maxRowsPerShard": 200,
                    "shardResults": [
                      {
                        "shardName": "shard1",
                        "status": "SUCCESS",
                        "columns": ["id"],
                        "rows": [{"id": "1"}],
                        "truncated": false
                      }
                    ]
                  },
                  "shardName": "missing-shard"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.NotFound, missingShardExportResponse.status)
        assertTrue(missingShardExportResponse.bodyAsText().contains("Результат для source 'missing-shard' не найден."))
    }

    private fun manualTransactionSqlConsoleService(config: SqlConsoleConfig): SqlConsoleService =
        SqlConsoleService(
            config = config,
            executor = object : ShardSqlExecutor, com.sbrf.lt.datapool.sqlconsole.ShardSqlTransactionalExecutor {
                override fun execute(
                    shard: com.sbrf.lt.datapool.sqlconsole.ResolvedSqlConsoleShardConfig,
                    statement: com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatement,
                    fetchSize: Int,
                    maxRows: Int,
                    queryTimeoutSec: Int?,
                    executionControl: com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl,
                ): RawShardExecutionResult =
                    RawShardExecutionResult(
                        shardName = shard.name,
                        status = "SUCCESS",
                        columns = listOf("id"),
                        rows = listOf(mapOf("id" to "1")),
                    )

                override fun executeScriptInTransaction(
                    shard: com.sbrf.lt.datapool.sqlconsole.ResolvedSqlConsoleShardConfig,
                    statements: List<com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatement>,
                    fetchSize: Int,
                    maxRows: Int,
                    queryTimeoutSec: Int?,
                    executionPolicy: com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy,
                    executionControl: com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl,
                ): com.sbrf.lt.datapool.sqlconsole.TransactionalShardScriptExecution =
                    com.sbrf.lt.datapool.sqlconsole.TransactionalShardScriptExecution(
                        results = statements.map {
                            RawShardExecutionResult(
                                shardName = shard.name,
                                status = "SUCCESS",
                                affectedRows = 1,
                                message = "ok",
                            )
                        },
                        pendingTransaction = object : com.sbrf.lt.datapool.sqlconsole.PendingShardTransaction {
                            override val shardName: String = shard.name

                            override fun commit() = Unit

                            override fun rollback() = Unit
                        },
                    )
            },
        )

    private fun assertFinalRouteControlPathCleared(body: String, expectedTransactionState: String) {
        assertTrue(body.contains(""""transactionState":"$expectedTransactionState""""), body)
        assertTrue(body.jsonFieldIsNullOrMissing("ownerToken"), body)
        assertTrue(body.jsonFieldIsNullOrMissing("ownerLeaseExpiresAt"), body)
        assertTrue(body.jsonFieldIsNullOrMissing("pendingCommitExpiresAt"), body)
    }

    private fun String.jsonFieldIsNullOrMissing(name: String): Boolean {
        val node = createUiServerObjectMapper().readTree(this)
        return !node.has(name) || node.get(name).isNull
    }

    private fun String.jsonStringField(name: String): String? =
        Regex(""""$name":"([^"]+)"""").find(this)?.groupValues?.get(1)
}
