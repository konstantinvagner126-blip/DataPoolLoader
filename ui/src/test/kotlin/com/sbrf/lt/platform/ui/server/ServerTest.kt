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
import com.sbrf.lt.datapool.db.PostgresExporter
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiActorIdentity
import com.sbrf.lt.platform.ui.config.UiActorResolver
import com.sbrf.lt.platform.ui.config.UiActorSource
import com.sbrf.lt.platform.ui.config.UiDatabaseConnectionChecker
import com.sbrf.lt.platform.ui.config.UiDatabaseConnectionStatus
import com.sbrf.lt.platform.ui.config.UiDatabaseSchemaMigrator
import com.sbrf.lt.platform.ui.config.UiConfigPersistenceService
import com.sbrf.lt.platform.ui.config.UiModuleStoreConfig
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiModuleStorePostgresConfig
import com.sbrf.lt.platform.ui.config.UiRuntimeContextService
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.model.ModuleCatalogItemResponse
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleFileContent
import com.sbrf.lt.platform.ui.model.SqlConsoleQueryRequest
import com.sbrf.lt.platform.ui.module.DatabaseConnectionProvider
import com.sbrf.lt.platform.ui.module.DatabaseEditableModule
import com.sbrf.lt.platform.ui.module.DatabaseModuleStore
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import com.sbrf.lt.platform.ui.run.RunManager
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.nio.file.Files
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
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
        assertTrue(homeHtml.contains("Загрузка данных"))
        assertTrue(homeHtml.contains("SQL-консоль"))
        assertTrue(homeHtml.contains("Справка"))

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
                  }
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
                                message = "Параметры PostgreSQL registry не настроены.",
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
        assertTrue(runtimeContext.contains("Параметры PostgreSQL registry не настроены."))
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
                        message = "PostgreSQL registry доступен.",
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
                        message = "PostgreSQL registry доступен.",
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

        assertTrue(details.contains("\"effectiveMode\":\"database\""))
        assertTrue(details.contains("\"sourceKind\":\"CURRENT_REVISION\""))
        assertTrue(details.contains("\"currentRevisionId\":\"11111111-1111-1111-1111-111111111111\""))
        assertTrue(details.contains("\"configPath\":\"db:db-demo\""))
        assertTrue(details.contains("\"content\":\"select 1\""))
    }

    @Test
    fun `starts run through api and returns bad request for invalid requests`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val runManager = RunManager(
            moduleRegistry = registry,
            applicationRunner = ApplicationRunner(
                exporter = PostgresExporter { _, _, _ ->
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
