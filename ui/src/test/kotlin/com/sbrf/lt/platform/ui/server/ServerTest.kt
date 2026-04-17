package com.sbrf.lt.platform.ui.server

import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.RawShardConnectionCheckResult
import com.sbrf.lt.datapool.sqlconsole.ShardConnectionChecker
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionCancelledException
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.model.RegistryModuleCreationResult
import com.sbrf.lt.datapool.db.registry.model.RegistryModuleDraft
import com.sbrf.lt.datapool.module.sync.ActiveModuleSyncRun
import com.sbrf.lt.datapool.module.sync.ModuleRegistryImporter
import com.sbrf.lt.datapool.module.sync.ModuleSyncRunDetails
import com.sbrf.lt.datapool.module.sync.ModuleSyncRunSummary
import com.sbrf.lt.datapool.module.sync.ModuleSyncService
import com.sbrf.lt.datapool.module.sync.ModuleSyncState
import com.sbrf.lt.datapool.module.sync.SyncItemResult
import com.sbrf.lt.datapool.db.PostgresSourceExporter
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiActorIdentity
import com.sbrf.lt.platform.ui.config.UiActorResolver
import com.sbrf.lt.platform.ui.config.UiActorSource
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import com.sbrf.lt.platform.ui.config.UiDatabaseConnectionChecker
import com.sbrf.lt.platform.ui.config.UiDatabaseConnectionStatus
import com.sbrf.lt.platform.ui.config.UiDatabaseSchemaMigrator
import com.sbrf.lt.platform.ui.config.UiConfigPersistenceService
import com.sbrf.lt.platform.ui.config.UiModuleStoreConfig
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiModuleStorePostgresConfig
import com.sbrf.lt.platform.ui.config.UiRuntimeActorState
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.config.UiRuntimeContextService
import com.sbrf.lt.platform.ui.config.UiRuntimeConfigResolver
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.model.ModuleCatalogItemResponse
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import com.sbrf.lt.platform.ui.model.SqlConsoleQueryRequest
import com.sbrf.lt.platform.ui.model.StartRunRequest
import com.sbrf.lt.platform.ui.module.DatabaseEditableModule
import com.sbrf.lt.platform.ui.module.DatabaseModuleStore
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import com.sbrf.lt.platform.ui.module.backend.DatabaseModuleBackend
import com.sbrf.lt.platform.ui.run.DatabaseModuleExecutionSource
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunService
import com.sbrf.lt.platform.ui.run.DatabaseRunStore
import com.sbrf.lt.platform.ui.run.RunManager
import com.sbrf.lt.platform.ui.run.UiCredentialsService
import com.sbrf.lt.platform.ui.run.UiCredentialsProvider
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManager
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.nio.file.Files
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.time.Instant
import org.slf4j.helpers.NOPLogger

class ServerTest {

    @Test
    fun `loads static text and fails for missing resource`() {
        assertTrue(loadStaticText("static/home.html").contains("Load Testing Data Platform"))

        val error = assertFailsWith<IllegalStateException> {
            loadStaticText(
                "static/missing.html",
                object : ClassLoader(null) {
                    override fun getResourceAsStream(name: String?) = null
                },
            )
        }

        assertTrue(error.message!!.contains("static/missing.html"))
    }

    @Test
    fun `start ui server delegates startup to provided starter`() {
        val appsRoot = Files.createTempDirectory("ui-start-apps")
        val storageDir = Files.createTempDirectory("ui-start-storage")
        val ports = mutableListOf<Int>()
        var module: (Application.() -> Unit)? = null
        val starter = UiServerStarter { port, applicationModule ->
            ports += port
            module = applicationModule
        }

        startUiServer(
            uiConfig = UiAppConfig(
                port = 9191,
                appsRoot = appsRoot.toString(),
                storageDir = storageDir.toString(),
                sqlConsole = SqlConsoleConfig(),
            ),
            logger = NOPLogger.NOP_LOGGER,
            starter = starter,
        )
        startUiServer(
            uiConfig = UiAppConfig(
                port = 9292,
                storageDir = storageDir.toString(),
                sqlConsole = SqlConsoleConfig(),
            ),
            logger = NOPLogger.NOP_LOGGER,
            starter = starter,
        )

        assertEquals(listOf(9191, 9292), ports)
        assertNotNull(module)
    }

    @Test
    fun `startup module serves content and websocket publishes state updates`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-server-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        val runManager = RunManager(moduleRegistry = registry, uiConfig = uiConfig)
        val wsClient = createClient {
            install(WebSockets)
        }
        application(
            uiStartupModule(
                uiConfig = uiConfig,
                logger = NOPLogger.NOP_LOGGER,
                moduleInstaller = {
                    uiModule(
                        uiConfig = uiConfig,
                        moduleRegistry = registry,
                        runManager = runManager,
                    )
                },
            ),
        )

        val session = wsClient.webSocketSession("/ws")
        try {
            val initial = session.incoming.receive() as Frame.Text
            assertTrue(initial.readText().contains("\"history\":[]"))

            val uploadResponse = client.post("/api/credentials/upload") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "file",
                                "DB1_USERNAME=uploaded",
                                io.ktor.http.Headers.build {
                                    append(HttpHeaders.ContentDisposition, ContentDisposition.File.withParameter(ContentDisposition.Parameters.Name, "file").withParameter(ContentDisposition.Parameters.FileName, "credential.properties").toString())
                                    append(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                                },
                            )
                        },
                    ),
                )
            }
            assertEquals(HttpStatusCode.OK, uploadResponse.status)

            var uploadedSeen = false
            withTimeout(2_000) {
                while (true) {
                    val frame = session.incoming.receive()
                    if (frame is Frame.Text && frame.readText().contains("\"mode\":\"UPLOADED\"")) {
                        uploadedSeen = true
                        break
                    }
                }
            }
            assertTrue(uploadedSeen)
        } finally {
            session.cancel()
        }
    }

    @Test
    fun `serves html and module api`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val storageDir = Files.createTempDirectory("ui-server-state-")
        val uiConfig = UiAppConfig(
            storageDir = storageDir.toString(),
            sqlConsole = SqlConsoleConfig(
                queryTimeoutSec = 30,
                sources = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user", "pwd")),
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
                    )
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

        val homeHtml = client.get("/").bodyAsText()
        assertTrue(homeHtml.contains("Load Testing Data Platform"))
        assertTrue(homeHtml.contains("Загрузка дата пулов"))
        assertTrue(homeHtml.contains("Файловый режим"))
        assertTrue(homeHtml.contains("DB режим"))
        assertTrue(homeHtml.contains("SQL-консоль"))
        assertTrue(homeHtml.contains("Справка"))

        val noRedirectClient = createClient {
            followRedirects = false
        }
        val composeSpikeRedirect = noRedirectClient.get("/compose-spike")
        assertEquals(HttpStatusCode.Found, composeSpikeRedirect.status)
        assertEquals("/static/compose-spike/index.html", composeSpikeRedirect.headers[HttpHeaders.Location])

        val composeRunsRedirect = noRedirectClient.get("/compose-runs?storage=files&module=demo-app")
        assertEquals(HttpStatusCode.Found, composeRunsRedirect.status)
        assertEquals(
            "/static/compose-spike/index.html?screen=module-runs&storage=files&module=demo-app",
            composeRunsRedirect.headers[HttpHeaders.Location]
        )

        val composeEditorRedirect = noRedirectClient.get("/compose-editor?storage=files&module=demo-app")
        assertEquals(HttpStatusCode.Found, composeEditorRedirect.status)
        assertEquals(
            "/static/compose-spike/index.html?screen=module-editor&storage=files&module=demo-app",
            composeEditorRedirect.headers[HttpHeaders.Location]
        )

        val html = client.get("/modules").bodyAsText()
        assertTrue(html.contains("Редактор модуля"))

        val helpHtml = client.get("/help").bodyAsText()
        assertTrue(helpHtml.contains("Справка"))
        assertTrue(helpHtml.contains("Модуль загрузки данных"))
        assertTrue(helpHtml.contains("SQL-консоль"))
        assertTrue(helpHtml.contains("credential.properties"))

        val sqlConsoleHtml = client.get("/sql-console").bodyAsText()
        assertTrue(sqlConsoleHtml.contains("SQL-редактор"))

        val modules = client.get("/api/modules").bodyAsText()
        assertTrue(modules.contains("demo-app"))
        assertTrue(modules.contains("\"validationStatus\":\"VALID\""))

        val catalog = client.get("/api/modules/catalog").bodyAsText()
        assertTrue(catalog.contains("\"mode\":\"READY\""))
        assertTrue(catalog.contains("Доступно модулей: 1"))
        assertTrue(catalog.contains("demo-app"))
        assertTrue(catalog.contains("Учебный модуль для UI-тестов."))
        assertTrue(catalog.contains("\"tags\":[\"postgres\",\"demo\"]"))

        val runtimeContext = client.get("/api/ui/runtime-context").bodyAsText()
        assertTrue(runtimeContext.contains("\"requestedMode\":\"files\""))
        assertTrue(runtimeContext.contains("\"effectiveMode\":\"files\""))
        assertTrue(runtimeContext.contains("\"resolved\":true"))

        val details = client.get("/api/modules/demo-app").bodyAsText()
        assertTrue(details.contains("Общий SQL"))
        assertTrue(details.contains("Источник: db2"))
        assertTrue(details.contains("Учебный модуль для UI-тестов."))
        assertTrue(details.contains("\"validationStatus\":\"VALID\""))

        val info = client.get("/api/sql-console/info").bodyAsText()
        assertTrue(info.contains("\"sourceNames\":[\"shard1\"]"))
        assertTrue(info.contains("\"queryTimeoutSec\":30"))
        assertTrue(info.contains("\"maxRowsPerShard\":200"))

        val state = client.get("/api/sql-console/state").bodyAsText()
        assertTrue(state.contains("\"draftSql\":\"select 1 as check_value\""))
        assertTrue(state.contains("\"pageSize\":50"))

        val savedState = client.post("/api/sql-console/state") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "draftSql":"select * from demo",
                  "recentQueries":["select * from demo"],
                  "selectedSourceNames":["shard1"],
                  "pageSize":100
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, savedState.status)
        val savedStateBody = savedState.bodyAsText()
        assertTrue(savedStateBody.contains("\"draftSql\":\"select * from demo\""))
        assertTrue(savedStateBody.contains("\"pageSize\":100"))

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

        val queryResponse = client.post("/api/sql-console/query") {
            contentType(ContentType.Application.Json)
            setBody("""{"sql":"select 1 as id","selectedSourceNames":["shard1"]}""")
        }
        assertEquals(HttpStatusCode.OK, queryResponse.status)
        val queryBody = queryResponse.bodyAsText()
        assertTrue(queryBody.contains("\"statementType\":\"RESULT_SET\""))
        assertTrue(queryBody.contains("\"statementKeyword\":\"SELECT\""))
        assertTrue(queryBody.contains("\"shardName\":\"shard1\""))
        assertTrue(queryBody.contains("\"rows\":[{\"id\":\"1\"}]"))

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
    fun `uploads credentials and saves module files`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val runManager = RunManager(
            moduleRegistry = registry,
            uiConfig = UiAppConfig(storageDir = Files.createTempDirectory("ui-server-state-").toString()),
        )
        application {
            uiModule(moduleRegistry = registry, runManager = runManager)
        }

        val uploadResponse = client.post("/api/credentials/upload") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            "DB1_USERNAME=uploaded",
                            io.ktor.http.Headers.build {
                                append(HttpHeaders.ContentDisposition, ContentDisposition.File.withParameter(ContentDisposition.Parameters.Name, "file").withParameter(ContentDisposition.Parameters.FileName, "credential.properties").toString())
                                append(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                            },
                        )
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, uploadResponse.status)
        assertTrue(uploadResponse.bodyAsText().contains("\"mode\":\"UPLOADED\""))

        val saveResponse = client.post("/api/modules/demo-app/save") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "configText": "app:\n  commonSqlFile: classpath:sql/common.sql\n  sources:\n    - name: db2\n      sqlFile: classpath:sql/db2.sql\n",
                  "sqlFiles": {
                    "classpath:sql/common.sql": "select 10",
                    "classpath:sql/db2.sql": "select 20"
                  },
                  "title": "Demo App"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, saveResponse.status)
        val resources = root.resolve("apps/demo-app/src/main/resources")
        assertEquals("select 10", resources.resolve("sql/common.sql").readText())
        assertEquals("select 20", resources.resolve("sql/db2.sql").readText())
    }

    @Test
    fun `parses and updates config form state through api`() = testApplication {
        application {
            uiModule()
        }

        val parseResponse = client.post("/api/config-form/parse") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {"configText":"app:\n  outputDir: ./output\n  mergeMode: round_robin\n  sources:\n    - name: db1\n      jdbcUrl: ${'$'}{DB1_JDBC_URL}\n      username: ${'$'}{DB1_USERNAME}\n      password: ${'$'}{DB1_PASSWORD}\n  target:\n    enabled: true\n    table: public.sample\n    truncateBeforeLoad: true\n"}
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, parseResponse.status)
        val parseBody = parseResponse.bodyAsText()
        assertTrue(parseBody.contains("\"fileFormat\":\"csv\""))
        assertTrue(parseBody.contains("\"mergeMode\":\"round_robin\""))
        assertTrue(parseBody.contains("\"sources\":["))
        assertTrue(parseBody.contains("\"targetTable\":\"public.sample\""))

        val updateResponse = client.post("/api/config-form/update") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "configText": "app:\n  outputDir: ./output\n  mergeMode: plain\n  sources:\n    - name: db1\n      jdbcUrl: ${'$'}{DB1_JDBC_URL}\n      username: ${'$'}{DB1_USERNAME}\n      password: ${'$'}{DB1_PASSWORD}\n  target:\n    enabled: false\n    table: public.old_table\n    truncateBeforeLoad: false\n",
                  "formState": {
                    "outputDir": "./next-output",
                    "fileFormat": "csv",
                    "mergeMode": "quota",
                    "errorMode": "continue_on_error",
                    "parallelism": 6,
                    "fetchSize": 1500,
                    "queryTimeoutSec": 45,
                    "progressLogEveryRows": 2222,
                    "maxMergedRows": 3333,
                    "deleteOutputFilesAfterCompletion": true,
                    "commonSql": "select 5 as id",
                    "commonSqlFile": "classpath:sql/common.sql",
                    "sources": [
                      {
                        "name": "db1",
                        "jdbcUrl": "${'$'}{DB1_JDBC_URL}",
                        "username": "${'$'}{DB1_USERNAME}",
                        "password": "${'$'}{DB1_PASSWORD}",
                        "sql": "select 9",
                        "sqlFile": "classpath:sql/db1.sql"
                      }
                    ],
                    "quotas": [
                      {
                        "source": "db1",
                        "percent": 100.0
                      }
                    ],
                    "targetEnabled": true,
                    "targetJdbcUrl": "${'$'}{TARGET_JDBC_URL}",
                    "targetUsername": "${'$'}{TARGET_USERNAME}",
                    "targetPassword": "${'$'}{TARGET_PASSWORD}",
                    "targetTable": "public.new_table",
                    "targetTruncateBeforeLoad": true
                  }
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updateBody = updateResponse.bodyAsText()
        assertTrue(updateBody.contains("\"mergeMode\":\"quota\""))
        assertTrue(updateBody.contains("\"targetTable\":\"public.new_table\""))
        assertTrue(updateBody.contains("\"commonSql\":\"select 5 as id\""))
        assertTrue(updateBody.contains("\"quotas\":["))
        assertTrue(updateBody.contains("next-output"))
        assertTrue(updateBody.contains("DB1_JDBC_URL"))
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
                sources = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user", "pwd")),
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
        val queryManager = SqlConsoleQueryManager(sqlConsoleService)
        application {
            uiModule(
                moduleRegistry = registry,
                runManager = runManager,
                sqlConsoleService = sqlConsoleService,
                sqlConsoleQueryManager = queryManager,
            )
        }

        val stateBody = client.get("/api/state").bodyAsText()
        assertTrue(stateBody.contains("\"history\":[]"))

        val credentialsBody = client.get("/api/credentials").bodyAsText()
        assertTrue(credentialsBody.contains("\"mode\":\"NONE\""))

        val started = client.post("/api/sql-console/query/start") {
            contentType(ContentType.Application.Json)
            setBody("""{"sql":"delete from demo","selectedSourceNames":["shard1"]}""")
        }
        assertEquals(HttpStatusCode.OK, started.status)
        val startedBody = started.bodyAsText()
        assertTrue(startedBody.contains("\"status\":\"RUNNING\""))
        val executionId = Regex(""""id":"([^"]+)"""").find(startedBody)?.groupValues?.get(1)
        assertNotNull(executionId)

        val running = client.get("/api/sql-console/query/$executionId").bodyAsText()
        assertTrue(running.contains("\"status\":\"RUNNING\""))

        val duplicateStart = client.post("/api/sql-console/query/start") {
            contentType(ContentType.Application.Json)
            setBody("""{"sql":"delete from demo_again","selectedSourceNames":["shard1"]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, duplicateStart.status)
        assertTrue(duplicateStart.bodyAsText().contains("уже выполняется запрос"))

        val cancelled = client.post("/api/sql-console/query/$executionId/cancel").bodyAsText()
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
    fun `returns apps root diagnostics when modules path is not configured`() = testApplication {
        val uiConfig = UiAppConfig(storageDir = Files.createTempDirectory("ui-server-state-").toString())
        application {
            uiModule(
                uiConfig = uiConfig,
                moduleRegistry = ModuleRegistry(appsRoot = null),
                runManager = RunManager(uiConfig = uiConfig),
            )
        }

        val catalog = client.get("/api/modules/catalog").bodyAsText()
        assertTrue(catalog.contains("\"mode\":\"NOT_CONFIGURED\""))
        assertTrue(catalog.contains("Путь ui.appsRoot не задан"))
        assertTrue(catalog.contains("\"modules\":[]"))
    }

    @Test
    fun `modules page redirects to home when effective mode is database`() = testApplication {
        val noRedirectClient = createClient {
            followRedirects = false
        }
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-server-state-").toString(),
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.DATABASE),
            sqlConsole = SqlConsoleConfig(),
        )
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = object : UiRuntimeContextService() {
                    override fun resolve(uiConfig: UiAppConfig): UiRuntimeContext =
                        testRuntimeContext(UiModuleStoreMode.DATABASE)
                },
            )
        }

        val response = noRedirectClient.get("/modules")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/?modeAccessError=modules", response.headers[HttpHeaders.Location])
    }

    @Test
    fun `db pages redirect to home when effective mode is files`() = testApplication {
        val noRedirectClient = createClient {
            followRedirects = false
        }
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-server-state-").toString(),
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.FILES),
            sqlConsole = SqlConsoleConfig(),
        )
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = object : UiRuntimeContextService() {
                    override fun resolve(uiConfig: UiAppConfig): UiRuntimeContext =
                        testRuntimeContext(UiModuleStoreMode.FILES)
                },
            )
        }

        val dbModulesResponse = noRedirectClient.get("/db-modules")
        val dbCreateResponse = noRedirectClient.get("/db-modules/new")
        val dbSyncResponse = noRedirectClient.get("/db-sync")

        assertEquals(HttpStatusCode.Found, dbModulesResponse.status)
        assertEquals("/?modeAccessError=db-modules", dbModulesResponse.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.Found, dbCreateResponse.status)
        assertEquals("/?modeAccessError=db-modules", dbCreateResponse.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.Found, dbSyncResponse.status)
        assertEquals("/?modeAccessError=db-sync", dbSyncResponse.headers[HttpHeaders.Location])
    }

    @Test
    fun `db create module page is available in database mode`() = testApplication {
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-db-create-page-state-").toString(),
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.DATABASE),
            sqlConsole = SqlConsoleConfig(),
        )
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = object : UiRuntimeContextService() {
                    override fun resolve(uiConfig: UiAppConfig): UiRuntimeContext =
                        testRuntimeContext(UiModuleStoreMode.DATABASE)
                },
            )
        }

        val response = client.get("/db-modules/new")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Новый модуль"))
        assertTrue(response.bodyAsText().contains("createDbModuleForm"))
    }

    @Test
    fun `module runs page is available in files mode`() = testApplication {
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-runs-page-files-state-").toString(),
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.FILES),
            sqlConsole = SqlConsoleConfig(),
        )
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = object : UiRuntimeContextService() {
                    override fun resolve(uiConfig: UiAppConfig): UiRuntimeContext =
                        testRuntimeContext(UiModuleStoreMode.FILES)
                },
            )
        }

        val response = client.get("/module-runs?storage=files&module=demo-app")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("История и результаты"))
        assertTrue(response.bodyAsText().contains("runsPageModuleTitle"))
    }

    @Test
    fun `module runs page redirects when storage does not match effective mode`() = testApplication {
        val noRedirectClient = createClient {
            followRedirects = false
        }
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-runs-page-redirect-state-").toString(),
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.FILES),
            sqlConsole = SqlConsoleConfig(),
        )
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = object : UiRuntimeContextService() {
                    override fun resolve(uiConfig: UiAppConfig): UiRuntimeContext =
                        testRuntimeContext(UiModuleStoreMode.FILES)
                },
            )
        }

        val response = noRedirectClient.get("/module-runs?storage=database&module=db-demo")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/?modeAccessError=db-modules", response.headers[HttpHeaders.Location])
    }

    @Test
    fun `compose runs route redirects to compose bundle with module runs screen`() = testApplication {
        val noRedirectClient = createClient {
            followRedirects = false
        }
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-compose-runs-route-state-").toString(),
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.FILES),
            sqlConsole = SqlConsoleConfig(),
        )
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = object : UiRuntimeContextService() {
                    override fun resolve(uiConfig: UiAppConfig): UiRuntimeContext =
                        testRuntimeContext(UiModuleStoreMode.FILES)
                },
            )
        }

        val response = noRedirectClient.get("/compose-runs?storage=files&module=demo-app")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(
            "/static/compose-spike/index.html?screen=module-runs&storage=files&module=demo-app",
            response.headers[HttpHeaders.Location],
        )
    }

    @Test
    fun `compose editor route redirects to compose bundle with editor screen`() = testApplication {
        val noRedirectClient = createClient {
            followRedirects = false
        }
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-compose-editor-route-state-").toString(),
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.FILES),
            sqlConsole = SqlConsoleConfig(),
        )
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = object : UiRuntimeContextService() {
                    override fun resolve(uiConfig: UiAppConfig): UiRuntimeContext =
                        testRuntimeContext(UiModuleStoreMode.FILES)
                },
            )
        }

        val response = noRedirectClient.get("/compose-editor?storage=files&module=demo-app")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(
            "/static/compose-spike/index.html?screen=module-editor&storage=files&module=demo-app",
            response.headers[HttpHeaders.Location],
        )
    }

    @Test
    fun `module runs api returns unified files session history and details`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-runs-api-files-state-").toString(),
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.FILES),
            sqlConsole = SqlConsoleConfig(),
        )
        val runManager = RunManager(
            moduleRegistry = registry,
            applicationRunner = ApplicationRunner(
                exporter = PostgresSourceExporter { _, _, _ ->
                    exportConnection(
                        columns = listOf("id"),
                        rows = listOf(listOf(1)),
                    )
                },
            ),
            uiConfig = uiConfig,
        )
        val startedRun = runManager.startRun(
            StartRunRequest(
                moduleId = "demo-app",
                configText = """
                app:
                  commonSqlFile: classpath:sql/common.sql
                  sources:
                    - name: db2
                      sqlFile: classpath:sql/db2.sql
                """.trimIndent(),
                sqlFiles = mapOf(
                    "classpath:sql/common.sql" to "select 1",
                    "classpath:sql/db2.sql" to "select 2",
                ),
            ),
        )
        repeat(40) {
            if (runManager.currentState().history.any { it.id == startedRun.id && it.summaryJson != null }) {
                return@repeat
            }
            Thread.sleep(50)
        }

        application {
            uiModule(
                uiConfig = uiConfig,
                moduleRegistry = registry,
                runManager = runManager,
                runtimeContextService = object : UiRuntimeContextService() {
                    override fun resolve(uiConfig: UiAppConfig): UiRuntimeContext =
                        testRuntimeContext(UiModuleStoreMode.FILES)
                },
            )
        }

        val sessionResponse = client.get("/api/module-runs/files/demo-app")
        assertEquals(HttpStatusCode.OK, sessionResponse.status)
        assertTrue(sessionResponse.bodyAsText().contains("\"storageMode\":\"FILES\""))
        assertTrue(sessionResponse.bodyAsText().contains("\"moduleTitle\":\"Demo App\""))

        val historyResponse = client.get("/api/module-runs/files/demo-app/runs")
        assertEquals(HttpStatusCode.OK, historyResponse.status)
        assertTrue(historyResponse.bodyAsText().contains("\"moduleId\":\"demo-app\""))
        assertTrue(historyResponse.bodyAsText().contains("\"runId\":\"${startedRun.id}\""))

        val detailsResponse = client.get("/api/module-runs/files/demo-app/runs/${startedRun.id}")
        assertEquals(HttpStatusCode.OK, detailsResponse.status)
        assertTrue(detailsResponse.bodyAsText().contains("\"events\":"))
        assertTrue(detailsResponse.bodyAsText().contains("\"artifacts\":"))
    }

    @Test
    fun `module runs api returns unified database session history and details`() = testApplication {
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-runs-api-db-state-").toString(),
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.DATABASE),
            sqlConsole = SqlConsoleConfig(),
        )
        val runtimeContextService = object : UiRuntimeContextService() {
            override fun resolve(uiConfig: UiAppConfig): UiRuntimeContext =
                testRuntimeContext(UiModuleStoreMode.DATABASE)
        }
        val databaseBackend = DatabaseModuleBackend(
            object : DatabaseModuleStore(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ) {
                override fun loadModuleDetails(
                    moduleCode: String,
                    actorId: String,
                    actorSource: String,
                ) = com.sbrf.lt.platform.ui.module.DatabaseEditableModule(
                    module = ModuleDetailsResponse(
                        id = moduleCode,
                        title = "DB Demo",
                        configPath = "db:$moduleCode",
                        configText = "app:\n  sources: []",
                        sqlFiles = emptyList(),
                        requiresCredentials = false,
                        credentialsStatus = com.sbrf.lt.platform.ui.model.CredentialsStatusResponse(
                            mode = "NOT_FOUND",
                            displayName = "credential.properties не найден",
                            fileAvailable = false,
                            uploaded = false,
                        ),
                    ),
                    sourceKind = "CURRENT_REVISION",
                    currentRevisionId = "revision-1",
                )
            },
        )
        val runService = object : DatabaseModuleRunService(
            databaseModuleStore = DatabaseModuleStore(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
            executionSource = DatabaseModuleExecutionSource(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
            runStore = DatabaseRunStore(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
            credentialsProvider = object : UiCredentialsProvider {
                override fun materializeCredentialsFile(tempDir: java.nio.file.Path) = null
                override fun currentProperties(): Map<String, String> = emptyMap()
                override fun currentCredentialsStatus() = com.sbrf.lt.platform.ui.model.CredentialsStatusResponse(
                    mode = "NOT_FOUND",
                    displayName = "credential.properties не найден",
                    fileAvailable = false,
                    uploaded = false,
                )
            },
        ) {
            override fun listRuns(moduleCode: String, limit: Int): com.sbrf.lt.platform.ui.model.DatabaseModuleRunsResponse =
                com.sbrf.lt.platform.ui.model.DatabaseModuleRunsResponse(
                    moduleCode = moduleCode,
                    runs = listOf(
                        com.sbrf.lt.platform.ui.model.DatabaseModuleRunSummaryResponse(
                            runId = "run-1",
                            executionSnapshotId = "snapshot-1",
                            status = "SUCCESS",
                            launchSourceKind = "WORKING_COPY",
                            requestedAt = Instant.parse("2026-04-17T10:00:00Z"),
                            startedAt = Instant.parse("2026-04-17T10:00:01Z"),
                            finishedAt = Instant.parse("2026-04-17T10:01:00Z"),
                            moduleCode = moduleCode,
                            moduleTitle = "DB Demo",
                            outputDir = "/tmp/out",
                            mergedRowCount = 10,
                            successfulSourceCount = 1,
                            failedSourceCount = 0,
                            skippedSourceCount = 0,
                            targetStatus = "SUCCESS",
                            targetTableName = "demo_target",
                            targetRowsLoaded = 10,
                            errorMessage = null,
                        ),
                        com.sbrf.lt.platform.ui.model.DatabaseModuleRunSummaryResponse(
                            runId = "run-2",
                            executionSnapshotId = "snapshot-2",
                            status = "FAILED",
                            launchSourceKind = "CURRENT_REVISION",
                            requestedAt = Instant.parse("2026-04-16T10:00:00Z"),
                            startedAt = Instant.parse("2026-04-16T10:00:01Z"),
                            finishedAt = Instant.parse("2026-04-16T10:01:00Z"),
                            moduleCode = moduleCode,
                            moduleTitle = "DB Demo",
                            outputDir = "/tmp/out-2",
                            mergedRowCount = 0,
                            successfulSourceCount = 0,
                            failedSourceCount = 1,
                            skippedSourceCount = 0,
                            targetStatus = "FAILED",
                            targetTableName = "demo_target",
                            targetRowsLoaded = 0,
                            errorMessage = "boom",
                        ),
                    ).take(limit),
                )

            override fun loadRunDetails(
                moduleCode: String,
                runId: String,
            ): com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse =
                com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse(
                    run = com.sbrf.lt.platform.ui.model.DatabaseModuleRunSummaryResponse(
                        runId = runId,
                        executionSnapshotId = "snapshot-1",
                        status = "SUCCESS",
                        launchSourceKind = "WORKING_COPY",
                        requestedAt = Instant.parse("2026-04-17T10:00:00Z"),
                        startedAt = Instant.parse("2026-04-17T10:00:01Z"),
                        finishedAt = Instant.parse("2026-04-17T10:01:00Z"),
                        moduleCode = moduleCode,
                        moduleTitle = "DB Demo",
                        outputDir = "/tmp/out",
                        mergedRowCount = 10,
                        successfulSourceCount = 1,
                        failedSourceCount = 0,
                        skippedSourceCount = 0,
                        targetStatus = "SUCCESS",
                        targetTableName = "demo_target",
                        targetRowsLoaded = 10,
                        errorMessage = null,
                    ),
                    summaryJson = """{"mergedRowCount":10}""",
                    sourceResults = listOf(
                        com.sbrf.lt.platform.ui.model.DatabaseRunSourceResultResponse(
                            runSourceResultId = "source-1",
                            sourceName = "db1",
                            sortOrder = 0,
                            status = "SUCCESS",
                            startedAt = Instant.parse("2026-04-17T10:00:02Z"),
                            finishedAt = Instant.parse("2026-04-17T10:00:30Z"),
                            exportedRowCount = 10,
                            mergedRowCount = 10,
                            errorMessage = null,
                        ),
                    ),
                    events = listOf(
                        com.sbrf.lt.platform.ui.model.DatabaseRunEventResponse(
                            runEventId = "event-1",
                            seqNo = 1,
                            createdAt = Instant.parse("2026-04-17T10:00:01Z"),
                            stage = "RUN",
                            eventType = "RUN_FINISHED",
                            severity = "SUCCESS",
                            sourceName = null,
                            message = "DB-запуск завершен.",
                            payloadJson = mapOf("status" to "SUCCESS"),
                        ),
                    ),
                    artifacts = listOf(
                        com.sbrf.lt.platform.ui.model.DatabaseRunArtifactResponse(
                            runArtifactId = "artifact-1",
                            artifactKind = "SUMMARY_JSON",
                            artifactKey = "summary",
                            filePath = "/tmp/out/summary.json",
                            storageStatus = "PRESENT",
                            fileSizeBytes = 128,
                            contentHash = null,
                            createdAt = Instant.parse("2026-04-17T10:01:00Z"),
                        ),
                    ),
                )
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                databaseModuleRunService = runService,
                databaseModuleBackend = databaseBackend,
            )
        }

        val sessionResponse = client.get("/api/module-runs/database/db-demo")
        assertEquals(HttpStatusCode.OK, sessionResponse.status)
        assertTrue(sessionResponse.bodyAsText().contains("\"storageMode\":\"DATABASE\""))
        assertTrue(sessionResponse.bodyAsText().contains("\"moduleTitle\":\"DB Demo\""))

        val historyResponse = client.get("/api/module-runs/database/db-demo/runs?limit=1")
        assertEquals(HttpStatusCode.OK, historyResponse.status)
        assertTrue(historyResponse.bodyAsText().contains("\"activeRunId\":null"))
        assertTrue(historyResponse.bodyAsText().contains("\"executionSnapshotId\":\"snapshot-1\""))
        assertFalse(historyResponse.bodyAsText().contains("\"executionSnapshotId\":\"snapshot-2\""))

        val detailsResponse = client.get("/api/module-runs/database/db-demo/runs/run-1")
        assertEquals(HttpStatusCode.OK, detailsResponse.status)
        assertTrue(detailsResponse.bodyAsText().contains("\"summaryJson\":\"{\\\"mergedRowCount\\\":10}\""))
        assertTrue(detailsResponse.bodyAsText().contains("\"sourceName\":\"db1\""))
        assertTrue(detailsResponse.bodyAsText().contains("\"artifactKind\":\"SUMMARY_JSON\""))
    }

    @Test
    fun `api help page is available`() = testApplication {
        application {
            uiModule()
        }

        val response = client.get("/help/api")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("SwaggerUIBundle"))
        assertTrue(response.bodyAsText().contains("/static/openapi/ui-api-openapi.yaml"))
    }

    @Test
    fun `runtime context endpoint reports fallback to files when database mode is unavailable`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(),
            ),
            storageDir = Files.createTempDirectory("ui-runtime-context-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = UiRuntimeContextService(
                    actorResolver = object : com.sbrf.lt.platform.ui.config.UiActorResolver() {
                        override fun resolveAutomaticActor() = null
                    },
                    connectionChecker = object : UiDatabaseConnectionChecker() {
                        override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus {
                            return UiDatabaseConnectionStatus(
                                configured = false,
                                available = false,
                                schema = config.schemaName(),
                                message = "Параметры подключения к базе данных не настроены.",
                            )
                        }
                    },
                ),
            )
        }

        val runtimeContext = client.get("/api/ui/runtime-context").bodyAsText()

        assertTrue(runtimeContext.contains("\"requestedMode\":\"database\""))
        assertTrue(runtimeContext.contains("\"effectiveMode\":\"files\""))
        assertTrue(runtimeContext.contains("\"requiresManualInput\":true"))
        assertTrue(runtimeContext.contains("Параметры подключения к базе данных не настроены."))
    }

    @Test
    fun `runtime context switches to database after credentials upload`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "\${LOCAL_MANUAL_DB_JDBC_URL}",
                    username = "\${LOCAL_MANUAL_DB_USERNAME}",
                    password = "\${LOCAL_MANUAL_DB_PASSWORD}",
                ),
            ),
            storageDir = Files.createTempDirectory("ui-runtime-context-upload-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        val credentialsService = UiCredentialsService(uiConfigProvider = { uiConfig })
        val runtimeContextService = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity =
                    UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus {
                    val available = config.jdbcUrl == "jdbc:postgresql://127.0.0.1:5432/postgres" &&
                        config.username == "kwdev" &&
                        config.password == "dummy"
                    return UiDatabaseConnectionStatus(
                        configured = true,
                        available = available,
                        schema = config.schemaName(),
                        message = if (available) {
                            "Подключение к базе данных доступно."
                        } else {
                            "Подключение к базе данных недоступно."
                        },
                        errorMessage = if (available) null else "Не удалось разрешить placeholders.",
                    )
                }
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) = Unit
            },
        )
        application {
            uiModule(
                uiConfig = uiConfig,
                credentialsService = credentialsService,
                runtimeConfigResolver = UiRuntimeConfigResolver(credentialsService),
                runtimeContextService = runtimeContextService,
            )
        }

        val before = client.get("/api/ui/runtime-context").bodyAsText()
        val upload = client.post("/api/credentials/upload") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            """
                            LOCAL_MANUAL_DB_JDBC_URL=jdbc:postgresql://127.0.0.1:5432/postgres
                            LOCAL_MANUAL_DB_USERNAME=kwdev
                            LOCAL_MANUAL_DB_PASSWORD=dummy
                            """.trimIndent(),
                            io.ktor.http.Headers.build {
                                append(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.File
                                        .withParameter(ContentDisposition.Parameters.Name, "file")
                                        .withParameter(ContentDisposition.Parameters.FileName, "credential.properties")
                                        .toString(),
                                )
                                append(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                            },
                        )
                    },
                ),
            )
        }
        val after = client.get("/api/ui/runtime-context").bodyAsText()

        assertEquals(HttpStatusCode.OK, upload.status)
        assertTrue(before.contains("\"effectiveMode\":\"files\""))
        assertTrue(after.contains("\"effectiveMode\":\"database\""))
        assertTrue(after.contains("\"dbAvailable\":true") || after.contains("\"available\":true"))
    }

    @Test
    fun `runtime mode endpoint updates preferred mode and affects subsequent runtime context`() = testApplication {
        var currentConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.FILES,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                    username = "registry_user",
                    password = "registry_pwd",
                ),
            ),
            storageDir = Files.createTempDirectory("ui-runtime-mode-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        val uiConfigLoader = object : UiConfigLoader() {
            override fun load(): UiAppConfig = currentConfig
        }
        val runtimeContextService = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity =
                    UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schemaName(),
                        message = "Подключение к базе данных доступно.",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) = Unit
            },
        )
        val uiConfigPersistenceService = object : UiConfigPersistenceService(uiConfigLoader = uiConfigLoader) {
            override fun updateModuleStoreMode(mode: UiModuleStoreMode): UiAppConfig {
                currentConfig = currentConfig.copy(
                    moduleStore = currentConfig.moduleStore.copy(mode = mode),
                )
                return currentConfig
            }
        }
        application {
            uiModule(
                uiConfig = currentConfig,
                uiConfigLoader = uiConfigLoader,
                runtimeContextService = runtimeContextService,
                uiConfigPersistenceService = uiConfigPersistenceService,
            )
        }

        val before = client.get("/api/ui/runtime-context").bodyAsText()
        val update = client.post("/api/ui/runtime-mode") {
            contentType(ContentType.Application.Json)
            setBody("""{"mode":"database"}""")
        }
        val updateBody = update.bodyAsText()
        val after = client.get("/api/ui/runtime-context").bodyAsText()

        assertTrue(before.contains("\"requestedMode\":\"files\""))
        assertTrue(before.contains("\"effectiveMode\":\"files\""))
        assertEquals(HttpStatusCode.OK, update.status)
        assertTrue(updateBody.contains("\"runtimeContext\":{\"requestedMode\":\"database\""))
        assertTrue(updateBody.contains("\"effectiveMode\":\"database\""))
        assertTrue(after.contains("\"requestedMode\":\"database\""))
        assertTrue(after.contains("\"effectiveMode\":\"database\""))
    }

    @Test
    fun `db catalog endpoint returns modules when effective mode is database`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                    username = "registry_user",
                    password = "registry_pwd",
                ),
            ),
            storageDir = Files.createTempDirectory("ui-db-catalog-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        val runtimeContextService = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity =
                    UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schemaName(),
                        message = "Подключение к базе данных доступно.",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) = Unit
            },
        )
        val databaseModuleStore = object : DatabaseModuleStore(
            connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
        ) {
            override fun listModules(includeHidden: Boolean): List<ModuleCatalogItemResponse> =
                listOf(
                    ModuleCatalogItemResponse(
                        id = "db-demo",
                        title = "DB Demo",
                        description = "Модуль из БД",
                        tags = listOf("database"),
                    )
                )
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                databaseModuleStore = databaseModuleStore,
            )
        }

        val catalog = client.get("/api/db/modules/catalog").bodyAsText()

        assertTrue(catalog.contains("\"requestedMode\":\"database\""))
        assertTrue(catalog.contains("\"effectiveMode\":\"database\""))
        assertTrue(catalog.contains("\"id\":\"db-demo\""))
        assertTrue(catalog.contains("\"tags\":[\"database\"]"))
    }

    @Test
    fun `db endpoints expose sync state and block catalog during maintenance mode`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                    username = "registry_user",
                    password = "registry_pwd",
                ),
            ),
            storageDir = Files.createTempDirectory("ui-db-maintenance-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        val runtimeContextService = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity =
                    UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schemaName(),
                        message = "Подключение к базе данных доступно.",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) = Unit
            },
        )
        val moduleSyncService = object : ModuleSyncService(
            connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            moduleRegistryImporter = object : ModuleRegistryImporter {
                override fun createModule(
                    moduleCode: String,
                    actorId: String,
                    actorSource: String,
                    actorDisplayName: String?,
                    originKind: String,
                    draft: RegistryModuleDraft,
                ): RegistryModuleCreationResult = error("createModule must not be requested")
            },
        ) {
            override fun currentSyncState(): ModuleSyncState = ModuleSyncState(
                maintenanceMode = true,
                activeFullSync = ActiveModuleSyncRun(
                    syncRunId = "dddddddd-dddd-dddd-dddd-dddddddddddd",
                    scope = "ALL",
                    startedAt = Instant.parse("2026-04-16T10:15:30Z"),
                    startedByActorId = "kwdev",
                    startedByActorSource = "OS_LOGIN",
                    startedByActorDisplayName = "kwdev",
                ),
            )
        }
        val databaseModuleStore = object : DatabaseModuleStore(
            connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
        ) {
            override fun listModules(includeHidden: Boolean): List<ModuleCatalogItemResponse> =
                error("catalog must be blocked during maintenance mode")
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                databaseModuleStore = databaseModuleStore,
                moduleSyncService = moduleSyncService,
            )
        }

        val syncStateResponse = client.get("/api/db/sync/state")
        val catalogResponse = client.get("/api/db/modules/catalog")

        assertEquals(HttpStatusCode.OK, syncStateResponse.status)
        assertTrue(syncStateResponse.bodyAsText().contains("\"maintenanceMode\":true"))
        assertTrue(syncStateResponse.bodyAsText().contains("\"scope\":\"ALL\""))
        assertEquals(HttpStatusCode.BadRequest, catalogResponse.status)
        assertTrue(catalogResponse.bodyAsText().contains("идет массовый импорт модулей в БД"))
    }

    @Test
    fun `db endpoints expose active single sync state and block save for busy module`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                    username = "registry_user",
                    password = "registry_pwd",
                ),
            ),
            storageDir = Files.createTempDirectory("ui-db-single-sync-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        val runtimeContextService = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity =
                    UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schemaName(),
                        message = "Подключение к базе данных доступно.",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) = Unit
            },
        )
        val moduleSyncService = object : ModuleSyncService(
            connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            moduleRegistryImporter = object : ModuleRegistryImporter {
                override fun createModule(
                    moduleCode: String,
                    actorId: String,
                    actorSource: String,
                    actorDisplayName: String?,
                    originKind: String,
                    draft: RegistryModuleDraft,
                ): RegistryModuleCreationResult = error("createModule must not be requested")
            },
        ) {
            override fun currentSyncState(): ModuleSyncState = ModuleSyncState(
                activeSingleSyncs = listOf(
                    ActiveModuleSyncRun(
                        syncRunId = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
                        scope = "ONE",
                        startedAt = Instant.parse("2026-04-16T11:00:00Z"),
                        moduleCode = "db-demo",
                        startedByActorId = "other-user",
                        startedByActorSource = "OS_LOGIN",
                        startedByActorDisplayName = "other-user",
                    ),
                ),
            )
        }
        val databaseModuleStore = object : DatabaseModuleStore(
            connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
        ) {
            override fun saveWorkingCopy(
                moduleCode: String,
                actorId: String,
                actorSource: String,
                actorDisplayName: String?,
                request: com.sbrf.lt.platform.ui.model.SaveModuleRequest,
            ) {
                error("save must be blocked while module sync is active")
            }
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                databaseModuleStore = databaseModuleStore,
                moduleSyncService = moduleSyncService,
            )
        }

        val syncStateResponse = client.get("/api/db/sync/state")
        val saveResponse = client.post("/api/db/modules/db-demo/save") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "configText": "app:\n  mergeMode: plain\n",
                  "sqlFiles": {}
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, syncStateResponse.status)
        assertTrue(syncStateResponse.bodyAsText().contains("\"moduleCode\":\"db-demo\""))
        assertEquals(HttpStatusCode.BadRequest, saveResponse.status)
        assertTrue(saveResponse.bodyAsText().contains("Импорт модуля 'db-demo' уже выполняется"))
    }

    @Test
    fun `db sync history endpoints return runs and details`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                    username = "registry_user",
                    password = "registry_pwd",
                ),
            ),
            storageDir = Files.createTempDirectory("ui-db-sync-history-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        val runtimeContextService = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity =
                    UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schemaName(),
                        message = "Подключение к базе данных доступно.",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) = Unit
            },
        )
        val runSummary = ModuleSyncRunSummary(
            syncRunId = "99999999-9999-9999-9999-999999999999",
            scope = "ALL",
            status = "PARTIAL_SUCCESS",
            startedAt = Instant.parse("2026-04-17T09:00:00Z"),
            finishedAt = Instant.parse("2026-04-17T09:03:00Z"),
            startedByActorId = "kwdev",
            startedByActorSource = "OS_LOGIN",
            startedByActorDisplayName = "kwdev",
            totalProcessed = 3,
            totalCreated = 1,
            totalUpdated = 0,
            totalSkipped = 1,
            totalFailed = 1,
        )
        val moduleSyncService = object : ModuleSyncService(
            connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            moduleRegistryImporter = object : ModuleRegistryImporter {
                override fun createModule(
                    moduleCode: String,
                    actorId: String,
                    actorSource: String,
                    actorDisplayName: String?,
                    originKind: String,
                    draft: RegistryModuleDraft,
                ): RegistryModuleCreationResult = error("createModule must not be requested")
            },
        ) {
            override fun listSyncRuns(limit: Int): List<ModuleSyncRunSummary> = listOf(runSummary)

            override fun loadSyncRunDetails(syncRunId: String): ModuleSyncRunDetails? =
                ModuleSyncRunDetails(
                    run = runSummary,
                    items = listOf(
                        SyncItemResult(
                            moduleCode = "db-demo",
                            action = "CREATED",
                            status = "SUCCESS",
                            detectedHash = "hash-1",
                            resultRevisionId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                            details = mapOf("reason" to "module_created"),
                        ),
                    ),
                )
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                moduleSyncService = moduleSyncService,
            )
        }

        val historyResponse = client.get("/api/db/sync/runs?limit=50")
        val detailsResponse = client.get("/api/db/sync/runs/99999999-9999-9999-9999-999999999999")

        assertEquals(HttpStatusCode.OK, historyResponse.status)
        assertTrue(historyResponse.bodyAsText().contains("\"runs\":["))
        assertTrue(historyResponse.bodyAsText().contains("\"syncRunId\":\"99999999-9999-9999-9999-999999999999\""))
        assertTrue(historyResponse.bodyAsText().contains("\"totalProcessed\":3"))
        assertEquals(HttpStatusCode.OK, detailsResponse.status)
        assertTrue(detailsResponse.bodyAsText().contains("\"items\":["))
        assertTrue(detailsResponse.bodyAsText().contains("\"moduleCode\":\"db-demo\""))
    }

    @Test
    fun `db module details endpoint returns editable module for current actor`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                    username = "registry_user",
                    password = "registry_pwd",
                ),
            ),
            storageDir = Files.createTempDirectory("ui-db-details-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        val runtimeContextService = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity =
                    UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schemaName(),
                        message = "Подключение к базе данных доступно.",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) = Unit
            },
        )
        val databaseModuleStore = object : DatabaseModuleStore(
            connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
        ) {
            override fun loadModuleDetails(
                moduleCode: String,
                actorId: String,
                actorSource: String,
            ): DatabaseEditableModule {
                assertEquals("db-demo", moduleCode)
                assertEquals("kwdev", actorId)
                assertEquals("OS_LOGIN", actorSource)
                return DatabaseEditableModule(
                    module = ModuleDetailsResponse(
                        id = "db-demo",
                        title = "DB Demo",
                        configPath = "db:db-demo",
                        configText = "app:\n  mergeMode: plain\n",
                        sqlFiles = listOf(
                            ModuleFileContent(
                                label = "Общий SQL",
                                path = "common",
                                content = "select 1",
                                exists = true,
                            )
                        ),
                        requiresCredentials = false,
                        credentialsStatus = com.sbrf.lt.platform.ui.model.CredentialsStatusResponse(
                            mode = "NONE",
                            displayName = "Файл не задан",
                            fileAvailable = false,
                            uploaded = false,
                        ),
                    ),
                    sourceKind = "CURRENT_REVISION",
                    currentRevisionId = "11111111-1111-1111-1111-111111111111",
                )
            }
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                databaseModuleStore = databaseModuleStore,
            )
        }

        val details = client.get("/api/db/modules/db-demo").bodyAsText()

        assertTrue(details.contains("\"storageMode\":\"DATABASE\""))
        assertTrue(details.contains("\"sourceKind\":\"CURRENT_REVISION\""))
        assertTrue(details.contains("\"currentRevisionId\":\"11111111-1111-1111-1111-111111111111\""))
        assertTrue(details.contains("\"configPath\":\"db:db-demo\""))
        assertTrue(details.contains("\"content\":\"select 1\""))
    }

    @Test
    fun `db module create endpoint forwards full draft including hidden flag`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.DATABASE),
            storageDir = Files.createTempDirectory("ui-db-create-endpoint-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        val runtimeContextService = object : UiRuntimeContextService() {
            override fun resolve(uiConfig: UiAppConfig): UiRuntimeContext =
                testRuntimeContext(UiModuleStoreMode.DATABASE)
        }
        var capturedModuleCode: String? = null
        var capturedActorId: String? = null
        var capturedActorSource: String? = null
        var capturedDraft: RegistryModuleDraft? = null
        val databaseModuleStore = object : DatabaseModuleStore(
            connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
        ) {
            override fun createModule(
                moduleCode: String,
                actorId: String,
                actorSource: String,
                actorDisplayName: String?,
                originKind: String,
                request: RegistryModuleDraft,
            ): RegistryModuleCreationResult {
                capturedModuleCode = moduleCode
                capturedActorId = actorId
                capturedActorSource = actorSource
                capturedDraft = request
                return RegistryModuleCreationResult(
                    moduleId = "module-1",
                    moduleCode = moduleCode,
                    revisionId = "revision-1",
                    workingCopyId = "working-copy-1",
                )
            }
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                databaseModuleStore = databaseModuleStore,
            )
        }

        val response = client.post("/api/db/modules") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "moduleCode": "customer-pool",
                  "title": "Пул клиентов",
                  "description": "Тестовый DB-модуль",
                  "tags": ["demo", "nightly"],
                  "configText": "app:\n  outputDir: ./output\n  sources: []\n",
                  "hiddenFromUi": false
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("customer-pool", capturedModuleCode)
        assertEquals("kwdev", capturedActorId)
        assertEquals("OS_LOGIN", capturedActorSource)
        assertEquals("Пул клиентов", capturedDraft?.title)
        assertEquals("Тестовый DB-модуль", capturedDraft?.description)
        assertEquals(listOf("demo", "nightly"), capturedDraft?.tags)
        assertEquals(false, capturedDraft?.hiddenFromUi)
        assertTrue(capturedDraft?.configText?.contains("outputDir: ./output") == true)
        assertTrue(response.bodyAsText().contains("\"moduleCode\":\"customer-pool\""))
    }

    @Test
    fun `db module save endpoint stores personal working copy for current actor`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                    username = "registry_user",
                    password = "registry_pwd",
                ),
            ),
            storageDir = Files.createTempDirectory("ui-db-save-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        val runtimeContextService = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity =
                    UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schemaName(),
                        message = "Подключение к базе данных доступно.",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) = Unit
            },
        )
        var savedModuleCode: String? = null
        var savedActorId: String? = null
        var savedConfigText: String? = null
        val databaseModuleStore = object : DatabaseModuleStore(
            connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
        ) {
            override fun saveWorkingCopy(
                moduleCode: String,
                actorId: String,
                actorSource: String,
                actorDisplayName: String?,
                request: com.sbrf.lt.platform.ui.model.SaveModuleRequest,
            ) {
                savedModuleCode = moduleCode
                savedActorId = actorId
                savedConfigText = request.configText
                assertEquals("OS_LOGIN", actorSource)
                assertEquals("kwdev", actorDisplayName)
                assertEquals(mapOf("common" to "select 1"), request.sqlFiles)
                assertEquals("DB Demo", request.title)
            }
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                databaseModuleStore = databaseModuleStore,
            )
        }

        val response = client.post("/api/db/modules/db-demo/save") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "configText": "app:\n  mergeMode: plain\n",
                  "sqlFiles": {
                    "common": "select 1"
                  },
                  "title": "DB Demo"
                }
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("черновик"))
        assertEquals("db-demo", savedModuleCode)
        assertEquals("kwdev", savedActorId)
        assertEquals("app:\n  mergeMode: plain\n", savedConfigText)
    }

    @Test
    fun `db module discard endpoint removes personal working copy for current actor`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                    username = "registry_user",
                    password = "registry_pwd",
                ),
            ),
            storageDir = Files.createTempDirectory("ui-db-discard-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        val runtimeContextService = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity =
                    UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schemaName(),
                        message = "Подключение к базе данных доступно.",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) = Unit
            },
        )
        var discarded: Triple<String, String, String>? = null
        val databaseModuleStore = object : DatabaseModuleStore(
            connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
        ) {
            override fun discardWorkingCopy(moduleCode: String, actorId: String, actorSource: String) {
                discarded = Triple(moduleCode, actorId, actorSource)
            }
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                databaseModuleStore = databaseModuleStore,
            )
        }

        val response = client.post("/api/db/modules/db-demo/discard-working-copy")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("черновик"))
        assertEquals(Triple("db-demo", "kwdev", "OS_LOGIN"), discarded)
    }

    @Test
    fun `db module run endpoint starts database run for current actor`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                    username = "registry_user",
                    password = "registry_pwd",
                ),
            ),
            storageDir = Files.createTempDirectory("ui-db-run-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        val runtimeContextService = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity =
                    UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schemaName(),
                        message = "Подключение к базе данных доступно.",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) = Unit
            },
        )
        var startedBy: Triple<String, String, String>? = null
        val runService = object : DatabaseModuleRunService(
            databaseModuleStore = DatabaseModuleStore(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
            executionSource = DatabaseModuleExecutionSource(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
            runStore = DatabaseRunStore(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
            credentialsProvider = object : UiCredentialsProvider {
                override fun currentCredentialsStatus() =
                    com.sbrf.lt.platform.ui.model.CredentialsStatusResponse(
                        mode = "NONE",
                        displayName = "Файл не задан",
                        fileAvailable = false,
                        uploaded = false,
                    )

                override fun materializeCredentialsFile(tempDir: java.nio.file.Path) = null
            },
        ) {
            override fun startRun(
                moduleCode: String,
                actorId: String,
                actorSource: String,
                actorDisplayName: String?,
            ): com.sbrf.lt.platform.ui.model.DatabaseRunStartResponse {
                startedBy = Triple(moduleCode, actorId, actorSource)
                return com.sbrf.lt.platform.ui.model.DatabaseRunStartResponse(
                    runId = "run-1",
                    moduleCode = moduleCode,
                    status = "RUNNING",
                    requestedAt = Instant.parse("2026-04-17T10:00:00Z"),
                    launchSourceKind = "CURRENT_REVISION",
                    executionSnapshotId = "snapshot-1",
                    message = "Запуск DB-модуля '$moduleCode' начат.",
                )
            }
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                databaseModuleRunService = runService,
            )
        }

        val response = client.post("/api/db/modules/db-demo/run") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"runId\":\"run-1\""))
        assertEquals(Triple("db-demo", "kwdev", "OS_LOGIN"), startedBy)
    }

    @Test
    fun `db module runs endpoint returns stored run summaries`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                    username = "registry_user",
                    password = "registry_pwd",
                ),
            ),
            storageDir = Files.createTempDirectory("ui-db-runs-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        val runtimeContextService = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity =
                    UiActorIdentity("kwdev", UiActorSource.OS_LOGIN)
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schemaName(),
                        message = "Подключение к базе данных доступно.",
                    )
            },
            schemaMigrator = object : UiDatabaseSchemaMigrator() {
                override fun migrate(config: UiModuleStorePostgresConfig) = Unit
            },
        )
        val runService = object : DatabaseModuleRunService(
            databaseModuleStore = DatabaseModuleStore(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
            executionSource = DatabaseModuleExecutionSource(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
            runStore = DatabaseRunStore(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
            credentialsProvider = object : UiCredentialsProvider {
                override fun currentCredentialsStatus() =
                    com.sbrf.lt.platform.ui.model.CredentialsStatusResponse(
                        mode = "NONE",
                        displayName = "Файл не задан",
                        fileAvailable = false,
                        uploaded = false,
                    )

                override fun materializeCredentialsFile(tempDir: java.nio.file.Path) = null
            },
        ) {
            override fun listRuns(moduleCode: String, limit: Int): com.sbrf.lt.platform.ui.model.DatabaseModuleRunsResponse =
                com.sbrf.lt.platform.ui.model.DatabaseModuleRunsResponse(
                    moduleCode = moduleCode,
                    runs = listOf(
                        com.sbrf.lt.platform.ui.model.DatabaseModuleRunSummaryResponse(
                            runId = "run-1",
                            executionSnapshotId = "snapshot-1",
                            status = "SUCCESS",
                            launchSourceKind = "WORKING_COPY",
                            requestedAt = Instant.parse("2026-04-17T10:00:00Z"),
                            startedAt = Instant.parse("2026-04-17T10:00:01Z"),
                            finishedAt = Instant.parse("2026-04-17T10:01:00Z"),
                            moduleCode = moduleCode,
                            moduleTitle = "DB Demo",
                            outputDir = "/tmp/out",
                            mergedRowCount = 10,
                            successfulSourceCount = 1,
                            failedSourceCount = 0,
                            skippedSourceCount = 0,
                            targetStatus = "SUCCESS",
                            targetTableName = "demo_target",
                            targetRowsLoaded = 10,
                            errorMessage = null,
                        ),
                    ),
                )

            override fun loadRunDetails(
                moduleCode: String,
                runId: String,
            ): com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse =
                com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse(
                    run = com.sbrf.lt.platform.ui.model.DatabaseModuleRunSummaryResponse(
                        runId = runId,
                        executionSnapshotId = "snapshot-1",
                        status = "SUCCESS",
                        launchSourceKind = "WORKING_COPY",
                        requestedAt = Instant.parse("2026-04-17T10:00:00Z"),
                        startedAt = Instant.parse("2026-04-17T10:00:01Z"),
                        finishedAt = Instant.parse("2026-04-17T10:01:00Z"),
                        moduleCode = moduleCode,
                        moduleTitle = "DB Demo",
                        outputDir = "/tmp/out",
                        mergedRowCount = 10,
                        successfulSourceCount = 1,
                        failedSourceCount = 0,
                        skippedSourceCount = 0,
                        targetStatus = "SUCCESS",
                        targetTableName = "demo_target",
                        targetRowsLoaded = 10,
                        errorMessage = null,
                    ),
                    summaryJson = """{"mergedRowCount":10}""",
                    sourceResults = listOf(
                        com.sbrf.lt.platform.ui.model.DatabaseRunSourceResultResponse(
                            runSourceResultId = "source-1",
                            sourceName = "db1",
                            sortOrder = 0,
                            status = "SUCCESS",
                            startedAt = Instant.parse("2026-04-17T10:00:02Z"),
                            finishedAt = Instant.parse("2026-04-17T10:00:30Z"),
                            exportedRowCount = 10,
                            mergedRowCount = 10,
                            errorMessage = null,
                        ),
                    ),
                    events = listOf(
                        com.sbrf.lt.platform.ui.model.DatabaseRunEventResponse(
                            runEventId = "event-1",
                            seqNo = 1,
                            createdAt = Instant.parse("2026-04-17T10:00:01Z"),
                            stage = "RUN",
                            eventType = "RUN_FINISHED",
                            severity = "SUCCESS",
                            sourceName = null,
                            message = "DB-запуск завершен.",
                            payloadJson = mapOf("status" to "SUCCESS"),
                        ),
                    ),
                    artifacts = listOf(
                        com.sbrf.lt.platform.ui.model.DatabaseRunArtifactResponse(
                            runArtifactId = "artifact-1",
                            artifactKind = "SUMMARY_JSON",
                            artifactKey = "summary",
                            filePath = "/tmp/out/summary.json",
                            storageStatus = "PRESENT",
                            fileSizeBytes = 128,
                            contentHash = null,
                            createdAt = Instant.parse("2026-04-17T10:01:00Z"),
                        ),
                    ),
                )
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                databaseModuleRunService = runService,
            )
        }

        val response = client.get("/api/db/modules/db-demo/runs")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"moduleCode\":\"db-demo\""))
        assertTrue(response.bodyAsText().contains("\"runId\":\"run-1\""))
        assertTrue(response.bodyAsText().contains("\"launchSourceKind\":\"WORKING_COPY\""))

        val detailsResponse = client.get("/api/db/modules/db-demo/runs/run-1")

        assertEquals(HttpStatusCode.OK, detailsResponse.status)
        assertTrue(detailsResponse.bodyAsText().contains("\"summaryJson\":\"{\\\"mergedRowCount\\\":10}\""))
        assertTrue(detailsResponse.bodyAsText().contains("\"sourceName\":\"db1\""))
        assertTrue(detailsResponse.bodyAsText().contains("\"artifactKind\":\"SUMMARY_JSON\""))
    }

    @Test
    fun `starts run through api and returns bad request for invalid requests`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val runManager = RunManager(
            moduleRegistry = registry,
            applicationRunner = ApplicationRunner(
                exporter = PostgresSourceExporter { _, _, _ ->
                    exportConnection(
                        columns = listOf("id"),
                        rows = listOf(listOf(1)),
                    )
                },
            ),
            uiConfig = UiAppConfig(storageDir = Files.createTempDirectory("ui-server-state-").toString()),
        )
        application {
            uiModule(
                moduleRegistry = registry,
                runManager = runManager,
            )
        }

        val runResponse = client.post("/api/runs") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "moduleId": "demo-app",
                  "configText": "app:\n  commonSqlFile: classpath:sql/common.sql\n  sources:\n    - name: db2\n      sqlFile: classpath:sql/db2.sql\n",
                  "sqlFiles": {
                    "classpath:sql/common.sql": "select 1",
                    "classpath:sql/db2.sql": "select 2"
                  }
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, runResponse.status)
        assertTrue(runResponse.bodyAsText().contains("\"moduleId\":\"demo-app\""))

        val invalidModuleResponse = client.get("/api/modules/unknown")
        assertEquals(HttpStatusCode.BadRequest, invalidModuleResponse.status)
        assertTrue(invalidModuleResponse.bodyAsText().contains("не найден"))

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

        val emptyUploadResponse = client.post("/api/credentials/upload") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            "",
                            io.ktor.http.Headers.build {
                                append(HttpHeaders.ContentDisposition, ContentDisposition.File.withParameter(ContentDisposition.Parameters.Name, "file").withParameter(ContentDisposition.Parameters.FileName, "credential.properties").toString())
                                append(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                            },
                        )
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, emptyUploadResponse.status)
        assertTrue(emptyUploadResponse.bodyAsText().contains("Не удалось прочитать"))
    }

    private fun exportConnection(
        columns: List<String>,
        rows: List<List<Any?>>,
    ): Connection {
        var cursor = -1
        val metaData = Proxy.newProxyInstance(
            ResultSetMetaData::class.java.classLoader,
            arrayOf(ResultSetMetaData::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getColumnCount" -> columns.size
                "getColumnLabel" -> columns[(args?.get(0) as Int) - 1]
                else -> defaultValue(method.returnType)
            }
        } as ResultSetMetaData
        val resultSet = Proxy.newProxyInstance(
            ResultSet::class.java.classLoader,
            arrayOf(ResultSet::class.java),
        ) { _, method, args ->
            when (method.name) {
                "next" -> {
                    cursor++
                    cursor < rows.size
                }
                "getObject" -> rows[cursor][(args?.get(0) as Int) - 1]
                "getMetaData" -> metaData
                "close" -> null
                else -> defaultValue(method.returnType)
            }
        } as ResultSet
        val statement = Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "executeQuery" -> resultSet
                "setFetchSize", "close" -> null
                else -> defaultValue(method.returnType)
            }
        } as PreparedStatement
        return Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "prepareStatement" -> statement
                "setAutoCommit", "close", "commit", "rollback" -> null
                "getAutoCommit" -> false
                else -> defaultValue(method.returnType)
            }
        } as Connection
    }

    private fun defaultValue(type: Class<*>): Any? = when {
        type == java.lang.Boolean.TYPE -> false
        type == java.lang.Integer.TYPE -> 0
        type == java.lang.Long.TYPE -> 0L
        else -> null
    }

    private fun testRuntimeContext(mode: UiModuleStoreMode): UiRuntimeContext =
        UiRuntimeContext(
            requestedMode = mode,
            effectiveMode = mode,
            actor = UiRuntimeActorState(
                resolved = true,
                actorId = "kwdev",
                actorSource = "OS_LOGIN",
                actorDisplayName = "kwdev",
                message = "ok",
            ),
            database = UiDatabaseConnectionStatus(
                configured = mode == UiModuleStoreMode.DATABASE,
                available = mode == UiModuleStoreMode.DATABASE,
                schema = "ui_registry",
                message = if (mode == UiModuleStoreMode.DATABASE) "ok" else "db unavailable",
            ),
        )

    private fun createProject() = Files.createTempDirectory("ui-server").apply {
        resolve("settings.gradle.kts").writeText("rootProject.name = \"test\"")
        val appDir = resolve("apps/demo-app")
        appDir.createDirectories()
        appDir.resolve("build.gradle.kts").writeText("plugins { application }")
        appDir.resolve("ui-module.yml").writeText(
            """
            title: Demo App
            description: Учебный модуль для UI-тестов.
            tags:
              - postgres
              - demo
            """.trimIndent(),
        )
        val resources = appDir.resolve("src/main/resources").createDirectories()
        resources.resolve("application.yml").writeText(
            """
            app:
              commonSqlFile: classpath:sql/common.sql
              sources:
                - name: db2
                  sqlFile: classpath:sql/db2.sql
            """.trimIndent(),
        )
        resources.resolve("sql").createDirectories()
        resources.resolve("sql/common.sql").writeText("select 1")
        resources.resolve("sql/db2.sql").writeText("select 2")
    }
}
