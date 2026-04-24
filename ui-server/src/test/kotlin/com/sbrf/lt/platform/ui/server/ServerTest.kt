package com.sbrf.lt.platform.ui.server

import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.RawShardConnectionCheckResult
import com.sbrf.lt.datapool.sqlconsole.ShardConnectionChecker
import com.sbrf.lt.datapool.sqlconsole.ShardSqlObjectColumnLoader
import com.sbrf.lt.datapool.sqlconsole.ShardSqlObjectInspector
import com.sbrf.lt.datapool.sqlconsole.ShardSqlObjectSearchResult
import com.sbrf.lt.datapool.sqlconsole.ShardSqlObjectSearcher
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObject
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectColumn
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectConstraint
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectIndex
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectInspector
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleDatabaseObjectType
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionCancelledException
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceGroupConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.datapool.app.port.TargetImporter
import com.sbrf.lt.datapool.app.port.TargetSchemaValidator
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
import com.sbrf.lt.datapool.module.sync.SyncRunResult
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
import com.sbrf.lt.platform.ui.run.DatabaseOutputRetentionService
import com.sbrf.lt.platform.ui.run.DatabaseRunHistoryCleanupService
import com.sbrf.lt.platform.ui.run.DatabaseRunStore
import com.sbrf.lt.platform.ui.run.PersistedRunState
import com.sbrf.lt.platform.ui.run.RunManager
import com.sbrf.lt.platform.ui.run.RunStateStore
import com.sbrf.lt.platform.ui.run.UiCredentialsService
import com.sbrf.lt.platform.ui.run.UiCredentialsProvider
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleQueryManager
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExecutionHistoryService
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.datapool.model.TargetLoadSummary
import com.sbrf.lt.datapool.model.SourceExecutionResult
import com.sbrf.lt.datapool.app.SourceExportProgressEvent
import com.sbrf.lt.datapool.app.SourceExportStartedEvent
import org.slf4j.helpers.NOPLogger

class ServerTest {

    @Test
    fun `loads static text and fails for missing resource`() {
        assertTrue(loadStaticText("static/compose-app/index.html").contains("compose-ui-web"))

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
        val runtimeContext = buildUiServerStartupRuntime(uiConfig, UiRuntimeContextService()).runtimeContext
        val runManager = RunManager(moduleRegistry = registry, uiConfig = uiConfig)
        val wsClient = createClient {
            install(WebSockets)
        }
        application(
            uiStartupModule(
                uiConfig = uiConfig,
                logger = NOPLogger.NOP_LOGGER,
                runtimeContext = runtimeContext,
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
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-server-state-").toString(),
        )
        val runManager = RunManager(moduleRegistry = registry, uiConfig = uiConfig)
        application {
            uiModule(
                uiConfig = uiConfig,
                moduleRegistry = registry,
                runManager = runManager,
            )
        }

        val noRedirectClient = createClient {
            followRedirects = false
        }
        val homeRedirect = noRedirectClient.get("/")
        assertEquals(HttpStatusCode.Found, homeRedirect.status)
        assertEquals("/static/compose-app/index.html", homeRedirect.headers[HttpHeaders.Location])

        val aboutRedirect = noRedirectClient.get("/about")
        assertEquals(HttpStatusCode.Found, aboutRedirect.status)
        assertEquals(
            "/static/compose-app/index.html?screen=about",
            aboutRedirect.headers[HttpHeaders.Location],
        )

        val modulesRedirect = noRedirectClient.get("/modules")
        assertEquals(HttpStatusCode.Found, modulesRedirect.status)
        assertEquals(
            "/static/compose-app/index.html?screen=module-editor&storage=files",
            modulesRedirect.headers[HttpHeaders.Location],
        )

        val moduleRunsRedirect = noRedirectClient.get("/module-runs?storage=files&module=demo-app")
        assertEquals(HttpStatusCode.Found, moduleRunsRedirect.status)
        assertEquals(
            "/static/compose-app/index.html?screen=module-runs&storage=files&module=demo-app",
            moduleRunsRedirect.headers[HttpHeaders.Location]
        )

        val dbSyncRedirect = noRedirectClient.get("/db-sync")
        assertEquals(HttpStatusCode.Found, dbSyncRedirect.status)
        assertEquals(
            "/static/compose-app/index.html?modeAccessError=db-sync",
            dbSyncRedirect.headers[HttpHeaders.Location]
        )

        val removedStaticCompatibilityRedirect = noRedirectClient.get("/static/compose-spike/index.html?screen=sql-console")
        assertEquals(HttpStatusCode.NotFound, removedStaticCompatibilityRedirect.status)

        val removedComposeRunsRedirect = noRedirectClient.get("/compose-runs?storage=files&module=demo-app")
        assertEquals(HttpStatusCode.NotFound, removedComposeRunsRedirect.status)

        val removedComposeEditorRedirect = noRedirectClient.get("/compose-editor?storage=files&module=demo-app")
        assertEquals(HttpStatusCode.NotFound, removedComposeEditorRedirect.status)

        val removedComposeSyncRedirect = noRedirectClient.get("/compose-sync")
        assertEquals(HttpStatusCode.NotFound, removedComposeSyncRedirect.status)

        val helpHtml = client.get("/help").bodyAsText()
        assertTrue(helpHtml.contains("Справка"))
        assertTrue(helpHtml.contains("Модуль загрузки данных"))
        assertTrue(helpHtml.contains("SQL-консоль"))
        assertTrue(helpHtml.contains("credential.properties"))

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
    fun `config form update ignores unknown fields in request body`() = testApplication {
        application {
            uiModule()
        }

        val updateResponse = client.post("/api/config-form/update") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "configText": "app:\n  outputDir: ./output\n",
                  "formState": {
                    "outputDir": "./output",
                    "fileFormat": "csv",
                    "mergeMode": "plain",
                    "errorMode": "continue_on_error",
                    "parallelism": 2,
                    "fetchSize": 1000,
                    "queryTimeoutSec": 30,
                    "progressLogEveryRows": 1000,
                    "maxMergedRows": null,
                    "deleteOutputFilesAfterCompletion": false,
                    "commonSql": "",
                    "commonSqlFile": null,
                    "sources": [],
                    "quotas": [],
                    "targetEnabled": false,
                    "targetJdbcUrl": "",
                    "targetUsername": "",
                    "targetPassword": "",
                    "targetTable": "",
                    "targetTruncateBeforeLoad": false,
                    "warnings": [],
                    "uiOnlySectionState": {
                      "sourcesExpanded": false
                    }
                  }
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val responseBody = updateResponse.bodyAsText()
        assertTrue(responseBody.contains("\"parallelism\":2"))
    }

    @Test
    fun `config form update accepts omitted empty collections`() = testApplication {
        application {
            uiModule()
        }

        val updateResponse = client.post("/api/config-form/update") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "configText": "app:\n  outputDir: ./output\n",
                  "formState": {
                    "outputDir": "./output",
                    "fileFormat": "csv",
                    "mergeMode": "plain",
                    "errorMode": "continue_on_error",
                    "parallelism": 6,
                    "fetchSize": 1000,
                    "queryTimeoutSec": 30,
                    "progressLogEveryRows": 1000,
                    "maxMergedRows": null,
                    "deleteOutputFilesAfterCompletion": false,
                    "commonSql": "",
                    "commonSqlFile": null,
                    "targetEnabled": false,
                    "targetJdbcUrl": "",
                    "targetUsername": "",
                    "targetPassword": "",
                    "targetTable": "",
                    "targetTruncateBeforeLoad": false
                  }
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val responseBody = updateResponse.bodyAsText()
        assertTrue(responseBody.contains("\"parallelism\":6"))
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
        assertEquals(
            "/static/compose-app/index.html?modeAccessError=modules",
            response.headers[HttpHeaders.Location],
        )
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
        assertEquals(
            "/static/compose-app/index.html?modeAccessError=db-modules",
            dbModulesResponse.headers[HttpHeaders.Location],
        )
        assertEquals(HttpStatusCode.Found, dbCreateResponse.status)
        assertEquals(
            "/static/compose-app/index.html?modeAccessError=db-modules",
            dbCreateResponse.headers[HttpHeaders.Location],
        )
        assertEquals(HttpStatusCode.Found, dbSyncResponse.status)
        assertEquals(
            "/static/compose-app/index.html?modeAccessError=db-sync",
            dbSyncResponse.headers[HttpHeaders.Location],
        )
    }

    @Test
    fun `db create module page redirects to compose editor create flow in database mode`() = testApplication {
        val noRedirectClient = createClient {
            followRedirects = false
        }
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

        val response = noRedirectClient.get("/db-modules/new")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(
            "/static/compose-app/index.html?screen=module-editor&storage=database&openCreate=true",
            response.headers[HttpHeaders.Location],
        )
    }

    @Test
    fun `module runs page redirects to compose bundle in files mode`() = testApplication {
        val noRedirectClient = createClient {
            followRedirects = false
        }
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

        val response = noRedirectClient.get("/module-runs?storage=files&module=demo-app")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(
            "/static/compose-app/index.html?screen=module-runs&storage=files&module=demo-app",
            response.headers[HttpHeaders.Location],
        )
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
        assertEquals(
            "/static/compose-app/index.html?modeAccessError=db-modules",
            response.headers[HttpHeaders.Location],
        )
    }

    @Test
    fun `module runs route redirects to compose bundle with module runs screen`() = testApplication {
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

        val response = noRedirectClient.get("/module-runs?storage=files&module=demo-app")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(
            "/static/compose-app/index.html?screen=module-runs&storage=files&module=demo-app",
            response.headers[HttpHeaders.Location],
        )
    }

    @Test
    fun `modules route redirects to compose bundle with editor screen`() = testApplication {
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

        val response = noRedirectClient.get("/modules?module=demo-app")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals(
            "/static/compose-app/index.html?screen=module-editor&storage=files&module=demo-app",
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
                targetTableValidator = TargetSchemaValidator { _, _, _, _, _ -> Unit },
                importer = TargetImporter { target, _, _, _, _, expectedRowCount, _ ->
                    TargetLoadSummary(
                        table = target.table,
                        status = ExecutionStatus.SUCCESS,
                        rowCount = expectedRowCount,
                        finishedAt = Instant.now(),
                        enabled = true,
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
                      jdbcUrl: jdbc:postgresql://localhost:5432/source
                      username: source_user
                      password: source_password
                      sqlFile: classpath:sql/db2.sql
                  target:
                    enabled: true
                    jdbcUrl: jdbc:postgresql://localhost:5432/target
                    username: target_user
                    password: target_password
                    table: public.demo_target
                    truncateBeforeLoad: true
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
        val detailsBody = detailsResponse.bodyAsText()
        assertTrue(detailsBody.contains("\"events\":"))
        assertTrue(detailsBody.contains("\"artifacts\":"))
        assertFalse(detailsBody.contains("\"timestamp\":null"))
        assertTrue(detailsBody.contains("\"targetStatus\":\"SUCCESS\""))
        assertTrue(detailsBody.contains("\"targetTableName\":\"public.demo_target\""))
        assertTrue(detailsBody.contains("\"targetRowsLoaded\":1"))
    }

    @Test
    fun `module runs api returns not found for missing files run`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-runs-api-files-not-found-state-").toString(),
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.FILES),
            sqlConsole = SqlConsoleConfig(),
        )
        val runManager = RunManager(
            moduleRegistry = registry,
            uiConfig = uiConfig,
        )

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

        val detailsResponse = client.get("/api/module-runs/files/demo-app/runs/missing-run")

        assertEquals(HttpStatusCode.NotFound, detailsResponse.status)
        assertTrue(detailsResponse.bodyAsText().contains("Запуск 'missing-run' для модуля 'demo-app' не найден."))
    }

    @Test
    fun `module runs api returns bad request for unknown storage mode`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-runs-api-invalid-storage-state-").toString(),
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.FILES),
            sqlConsole = SqlConsoleConfig(),
        )
        val runManager = RunManager(
            moduleRegistry = registry,
            uiConfig = uiConfig,
        )

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

        val sessionResponse = client.get("/api/module-runs/legacy/demo-app")

        assertEquals(HttpStatusCode.BadRequest, sessionResponse.status)
        assertTrue(sessionResponse.bodyAsText().contains("Неизвестный режим хранения 'legacy'."))
    }

    @Test
    fun `module runs api returns live files details for active run`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-runs-api-files-live-state-").toString(),
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.FILES),
            sqlConsole = SqlConsoleConfig(),
        )
        val progressLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)
        val runManager = RunManager(
            moduleRegistry = registry,
            applicationRunner = ApplicationRunner(
                exporter = { task ->
                    val startedAt = Instant.now()
                    task.executionListener.onEvent(SourceExportStartedEvent(startedAt, task.source.name))
                    task.executionListener.onEvent(SourceExportProgressEvent(Instant.now(), task.source.name, 1))
                    progressLatch.countDown()
                    check(releaseLatch.await(5, TimeUnit.SECONDS)) { "Timed out waiting to release exporter" }
                    Files.writeString(task.outputFile, "id\n1\n")
                    SourceExecutionResult(
                        sourceName = task.source.name,
                        status = ExecutionStatus.SUCCESS,
                        rowCount = 1,
                        outputFile = task.outputFile,
                        columns = listOf("id"),
                        startedAt = startedAt,
                        finishedAt = Instant.now(),
                    )
                },
                targetTableValidator = TargetSchemaValidator { _, _, _, _, _ -> Unit },
                importer = TargetImporter { target, _, _, _, _, expectedRowCount, _ ->
                    TargetLoadSummary(
                        table = target.table,
                        status = ExecutionStatus.SUCCESS,
                        rowCount = expectedRowCount,
                        finishedAt = Instant.now(),
                        enabled = true,
                    )
                },
            ),
            uiConfig = uiConfig,
        )

        try {
            val startedRun = runManager.startRun(
                StartRunRequest(
                    moduleId = "demo-app",
                    configText = """
                    app:
                      commonSqlFile: classpath:sql/common.sql
                      sources:
                        - name: db2
                          jdbcUrl: jdbc:postgresql://localhost:5432/source
                          username: source_user
                          password: source_password
                          sqlFile: classpath:sql/db2.sql
                      target:
                        enabled: true
                        jdbcUrl: jdbc:postgresql://localhost:5432/target
                        username: target_user
                        password: target_password
                        table: public.demo_target
                        truncateBeforeLoad: true
                    """.trimIndent(),
                    sqlFiles = mapOf(
                        "classpath:sql/common.sql" to "select 1",
                        "classpath:sql/db2.sql" to "select 2",
                    ),
                ),
            )
            assertTrue(progressLatch.await(2, TimeUnit.SECONDS))

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

            val detailsResponse = client.get("/api/module-runs/files/demo-app/runs/${startedRun.id}")
            assertEquals(HttpStatusCode.OK, detailsResponse.status)
            val detailsBody = detailsResponse.bodyAsText()
            assertFalse(detailsBody.contains("\"timestamp\":null"))
            assertTrue(detailsBody.contains("\"status\":\"RUNNING\""))
            assertTrue(detailsBody.contains("\"targetStatus\":\"PENDING\""))
            assertTrue(detailsBody.contains("\"sourceName\":\"db2\""))
            assertTrue(detailsBody.contains("\"rowCount\":1"))
        } finally {
            releaseLatch.countDown()
        }
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
                        descriptor = com.sbrf.lt.platform.ui.model.ModuleMetadataDescriptorResponse(
                            title = "DB Demo",
                        ),
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
            runExecutionStore = DatabaseRunStore(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
            runQueryStore = DatabaseRunStore(
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
    fun `module runs api returns live database details for active run`() = testApplication {
        val uiConfig = UiAppConfig(
            storageDir = Files.createTempDirectory("ui-runs-api-db-active-state-").toString(),
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
                ) = DatabaseEditableModule(
                    module = ModuleDetailsResponse(
                        id = moduleCode,
                        descriptor = com.sbrf.lt.platform.ui.model.ModuleMetadataDescriptorResponse(
                            title = "DB Demo Active",
                        ),
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
                    currentRevisionId = "revision-active",
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
            runExecutionStore = DatabaseRunStore(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
            runQueryStore = DatabaseRunStore(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
            credentialsProvider = object : UiCredentialsProvider {
                override fun materializeCredentialsFile(tempDir: Path) = null

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
                            runId = "run-active",
                            executionSnapshotId = "snapshot-active",
                            status = "RUNNING",
                            launchSourceKind = "WORKING_COPY",
                            requestedAt = Instant.parse("2026-04-19T08:00:00Z"),
                            startedAt = Instant.parse("2026-04-19T08:00:02Z"),
                            finishedAt = null,
                            moduleCode = moduleCode,
                            moduleTitle = "DB Demo Active",
                            outputDir = "/tmp/out-active",
                            mergedRowCount = 1234,
                            successfulSourceCount = 0,
                            failedSourceCount = 0,
                            skippedSourceCount = 0,
                            targetStatus = "RUNNING",
                            targetTableName = "demo_target_active",
                            targetRowsLoaded = null,
                            errorMessage = null,
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
                        executionSnapshotId = "snapshot-active",
                        status = "RUNNING",
                        launchSourceKind = "WORKING_COPY",
                        requestedAt = Instant.parse("2026-04-19T08:00:00Z"),
                        startedAt = Instant.parse("2026-04-19T08:00:02Z"),
                        finishedAt = null,
                        moduleCode = moduleCode,
                        moduleTitle = "DB Demo Active",
                        outputDir = "/tmp/out-active",
                        mergedRowCount = 1234,
                        successfulSourceCount = 0,
                        failedSourceCount = 0,
                        skippedSourceCount = 0,
                        targetStatus = "RUNNING",
                        targetTableName = "demo_target_active",
                        targetRowsLoaded = null,
                        errorMessage = null,
                    ),
                    summaryJson = """{"mergedRowCount":1234}""",
                    sourceResults = listOf(
                        com.sbrf.lt.platform.ui.model.DatabaseRunSourceResultResponse(
                            runSourceResultId = "source-active-1",
                            sourceName = "db3",
                            sortOrder = 0,
                            status = "RUNNING",
                            startedAt = Instant.parse("2026-04-19T08:00:03Z"),
                            finishedAt = null,
                            exportedRowCount = 10000,
                            mergedRowCount = null,
                            errorMessage = null,
                        ),
                    ),
                    events = listOf(
                        com.sbrf.lt.platform.ui.model.DatabaseRunEventResponse(
                            runEventId = "event-active-1",
                            seqNo = 1,
                            createdAt = Instant.parse("2026-04-19T08:00:05Z"),
                            stage = "SOURCE",
                            eventType = "SOURCE_PROGRESS",
                            severity = "INFO",
                            sourceName = "db3",
                            message = "Источник db3: выгружено 10000 строк.",
                            payloadJson = mapOf("rowCount" to 10000),
                        ),
                    ),
                    artifacts = emptyList(),
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

        val historyResponse = client.get("/api/module-runs/database/db-demo/runs?limit=1")
        assertEquals(HttpStatusCode.OK, historyResponse.status)
        assertTrue(historyResponse.bodyAsText().contains("\"activeRunId\":\"run-active\""))
        assertTrue(historyResponse.bodyAsText().contains("\"targetStatus\":\"RUNNING\""))

        val detailsResponse = client.get("/api/module-runs/database/db-demo/runs/run-active")
        assertEquals(HttpStatusCode.OK, detailsResponse.status)
        val detailsBody = detailsResponse.bodyAsText()
        assertTrue(detailsBody.contains("\"status\":\"RUNNING\""))
        assertTrue(detailsBody.contains("\"sourceName\":\"db3\""))
        assertTrue(detailsBody.contains("\"exportedRowCount\":10000"))
        assertTrue(detailsBody.contains("Источник db3: выгружено 10000 строк."))
        assertTrue(detailsBody.contains("\"targetTableName\":\"demo_target_active\""))
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
    fun `common maintenance endpoints respect requested database mode when database is unavailable`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(),
            ),
            storageDir = Files.createTempDirectory("ui-maintenance-db-fallback-state-").toString(),
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

        val cleanupPreview = client.get("/api/run-history/cleanup/preview")
        val cleanupExecute = client.post("/api/run-history/cleanup") {
            contentType(ContentType.Application.Json)
            setBody("""{"disableSafeguard":false}""")
        }
        val outputPreview = client.get("/api/output-retention/preview")
        val outputExecute = client.post("/api/output-retention") {
            contentType(ContentType.Application.Json)
            setBody("""{"disableSafeguard":false}""")
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, cleanupPreview.status)
        assertTrue(cleanupPreview.bodyAsText().contains("Режим базы данных сейчас недоступен."))

        assertEquals(HttpStatusCode.ServiceUnavailable, cleanupExecute.status)
        assertTrue(cleanupExecute.bodyAsText().contains("Режим базы данных сейчас недоступен."))

        assertEquals(HttpStatusCode.ServiceUnavailable, outputPreview.status)
        assertTrue(outputPreview.bodyAsText().contains("Режим базы данных сейчас недоступен."))

        assertEquals(HttpStatusCode.ServiceUnavailable, outputExecute.status)
        assertTrue(outputExecute.bodyAsText().contains("Режим базы данных сейчас недоступен."))
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
                        descriptor = com.sbrf.lt.platform.ui.model.ModuleMetadataDescriptorResponse(
                            title = "DB Demo",
                            description = "Модуль из БД",
                            tags = listOf("database"),
                        ),
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
        assertEquals(HttpStatusCode.Conflict, catalogResponse.status)
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
        assertEquals(HttpStatusCode.Conflict, saveResponse.status)
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
    fun `db sync details returns 404 for unknown sync run`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                    username = "registry_user",
                    password = "registry_pwd",
                    schema = "ui_registry",
                ),
            ),
        )
        val runtimeContextService = UiRuntimeContextService(
            actorResolver = object : UiActorResolver() {
                override fun resolveAutomaticActor(): UiActorIdentity =
                    UiActorIdentity(
                        actorId = "kwdev",
                        actorSource = UiActorSource.OS_LOGIN,
                        actorDisplayName = "kwdev",
                    )
            },
            connectionChecker = object : UiDatabaseConnectionChecker() {
                override fun check(config: UiModuleStorePostgresConfig): UiDatabaseConnectionStatus =
                    UiDatabaseConnectionStatus(
                        configured = true,
                        available = true,
                        schema = config.schema ?: "ui_registry",
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
            override fun loadSyncRunDetails(syncRunId: String): ModuleSyncRunDetails? = null
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                moduleSyncService = moduleSyncService,
            )
        }

        val detailsResponse = client.get("/api/db/sync/runs/missing-sync-run")

        assertEquals(HttpStatusCode.NotFound, detailsResponse.status)
        assertTrue(detailsResponse.bodyAsText().contains("История импорта 'missing-sync-run' не найдена."))
    }

    @Test
    fun `db sync selected endpoint starts sync for chosen file modules`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                    username = "registry_user",
                    password = "registry_pwd",
                ),
            ),
            appsRoot = Files.createTempDirectory("ui-db-sync-selected-apps-").toString(),
            storageDir = Files.createTempDirectory("ui-db-sync-selected-state-").toString(),
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
            override fun syncSelectedFromFiles(
                moduleCodes: List<String>,
                appsRoot: Path,
                actorId: String,
                actorSource: String,
                actorDisplayName: String?,
            ): SyncRunResult {
                assertEquals(listOf("alpha", "beta"), moduleCodes)
                assertEquals("kwdev", actorId)
                assertEquals("OS_LOGIN", actorSource)
                return SyncRunResult(
                    syncRunId = "12121212-3434-5656-7878-909090909090",
                    scope = "SELECTED",
                    status = "SUCCESS",
                    startedAt = Instant.parse("2026-04-17T11:00:00Z"),
                    finishedAt = Instant.parse("2026-04-17T11:01:00Z"),
                    items = listOf(
                        SyncItemResult(moduleCode = "alpha", action = "CREATED", status = "SUCCESS", detectedHash = "hash-a"),
                        SyncItemResult(moduleCode = "beta", action = "CREATED", status = "SUCCESS", detectedHash = "hash-b"),
                    ),
                    totalProcessed = 2,
                    totalCreated = 2,
                    totalUpdated = 0,
                    totalSkipped = 0,
                    totalFailed = 0,
                )
            }
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                moduleSyncService = moduleSyncService,
            )
        }

        val response = client.post("/api/db/sync/selected") {
            contentType(ContentType.Application.Json)
            setBody("""{"moduleCodes":["alpha","beta"]}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"scope\":\"SELECTED\""))
        assertTrue(response.bodyAsText().contains("\"totalProcessed\":2"))
    }

    @Test
    fun `db run history cleanup endpoints return preview and execute cleanup`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                    username = "registry_user",
                    password = "registry_pwd",
                ),
            ),
            storageDir = Files.createTempDirectory("ui-db-run-cleanup-state-").toString(),
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
        val cleanupService = object : DatabaseRunHistoryCleanupService(
            runStore = DatabaseRunStore(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
        ) {
            override fun previewCleanup(disableSafeguard: Boolean) =
                com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupPreviewResponse(
                    safeguardEnabled = !disableSafeguard,
                    retentionDays = 30,
                    keepMinRunsPerModule = 30,
                    cutoffTimestamp = Instant.parse("2026-03-20T00:00:00Z"),
                    totalModulesAffected = 2,
                    totalRunsToDelete = 12,
                    totalSourceResultsToDelete = 48,
                    totalEventsToDelete = 96,
                    totalArtifactsToDelete = 24,
                    totalOrphanExecutionSnapshotsToDelete = 3,
                    modules = listOf(
                        com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupModuleResponse(
                            moduleCode = "alpha",
                            totalRunsToDelete = 7,
                            oldestRequestedAt = Instant.parse("2026-01-01T00:00:00Z"),
                            newestRequestedAt = Instant.parse("2026-02-01T00:00:00Z"),
                        ),
                    ),
                )

            override fun executeCleanup(disableSafeguard: Boolean) =
                com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse(
                    safeguardEnabled = !disableSafeguard,
                    retentionDays = 30,
                    keepMinRunsPerModule = 30,
                    cutoffTimestamp = Instant.parse("2026-03-20T00:00:00Z"),
                    finishedAt = Instant.parse("2026-04-19T12:00:00Z"),
                    totalModulesAffected = 2,
                    totalRunsDeleted = 12,
                    totalSourceResultsDeleted = 48,
                    totalEventsDeleted = 96,
                    totalArtifactsDeleted = 24,
                    totalOrphanExecutionSnapshotsDeleted = 3,
                    modules = listOf(
                        com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupModuleResponse(
                            moduleCode = "alpha",
                            totalRunsToDelete = 7,
                        ),
                    ),
                )
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                databaseRunHistoryCleanupService = cleanupService,
            )
        }

        val previewResponse = client.get("/api/db/run-history/cleanup/preview?disableSafeguard=true")
        val cleanupResponse = client.post("/api/db/run-history/cleanup") {
            contentType(ContentType.Application.Json)
            setBody("""{"disableSafeguard":true}""")
        }
        val commonPreviewResponse = client.get("/api/run-history/cleanup/preview?disableSafeguard=true")
        val commonCleanupResponse = client.post("/api/run-history/cleanup") {
            contentType(ContentType.Application.Json)
            setBody("""{"disableSafeguard":true}""")
        }

        assertEquals(HttpStatusCode.OK, previewResponse.status)
        assertTrue(previewResponse.bodyAsText().contains("\"totalRunsToDelete\":12"))
        assertTrue(previewResponse.bodyAsText().contains("\"moduleCode\":\"alpha\""))
        assertTrue(previewResponse.bodyAsText().contains("\"safeguardEnabled\":false"))

        assertEquals(HttpStatusCode.OK, cleanupResponse.status)
        assertTrue(cleanupResponse.bodyAsText().contains("\"totalRunsDeleted\":12"))
        assertTrue(cleanupResponse.bodyAsText().contains("\"totalOrphanExecutionSnapshotsDeleted\":3"))

        assertEquals(HttpStatusCode.OK, commonPreviewResponse.status)
        assertTrue(commonPreviewResponse.bodyAsText().contains("\"storageMode\":\"DATABASE\""))
        assertTrue(commonPreviewResponse.bodyAsText().contains("\"totalRunsToDelete\":12"))

        assertEquals(HttpStatusCode.OK, commonCleanupResponse.status)
        assertTrue(commonCleanupResponse.bodyAsText().contains("\"storageMode\":\"DATABASE\""))
        assertTrue(commonCleanupResponse.bodyAsText().contains("\"totalRunsDeleted\":12"))
    }

    @Test
    fun `common run history cleanup endpoints work in files mode`() = testApplication {
        val storageDir = Files.createTempDirectory("ui-files-run-cleanup-state-")
        val now = Instant.now()
        RunStateStore(storageDir).save(
            PersistedRunState(
                history = buildList {
                    repeat(32) { index ->
                        add(
                            com.sbrf.lt.platform.ui.model.UiRunSnapshot(
                                id = "run-$index",
                                moduleId = "files-alpha",
                                moduleTitle = "Files Alpha",
                                status = com.sbrf.lt.datapool.model.ExecutionStatus.SUCCESS,
                                startedAt = now.minusSeconds((40L + index) * 86_400),
                                finishedAt = now.minusSeconds((40L + index) * 86_400).plusSeconds(60),
                                events = listOf(mapOf("type" to "SourceExportProgressEvent")),
                            ),
                        )
                    }
                },
            ),
        )
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.FILES),
            storageDir = storageDir.toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        application {
            uiModule(
                uiConfig = uiConfig,
                runManager = RunManager(uiConfig = uiConfig),
            )
        }

        val previewResponse = client.get("/api/run-history/cleanup/preview")
        val cleanupResponse = client.post("/api/run-history/cleanup") {
            contentType(ContentType.Application.Json)
            setBody("""{"disableSafeguard":false}""")
        }

        assertEquals(HttpStatusCode.OK, previewResponse.status)
        assertTrue(previewResponse.bodyAsText().contains("\"storageMode\":\"FILES\""))
        assertTrue(previewResponse.bodyAsText().contains("\"totalRunsToDelete\":2"))

        assertEquals(HttpStatusCode.OK, cleanupResponse.status)
        assertTrue(cleanupResponse.bodyAsText().contains("\"storageMode\":\"FILES\""))
        assertTrue(cleanupResponse.bodyAsText().contains("\"totalRunsDeleted\":2"))
    }

    @Test
    fun `common output retention endpoints work in database mode`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(
                mode = UiModuleStoreMode.DATABASE,
                postgres = UiModuleStorePostgresConfig(
                    jdbcUrl = "jdbc:postgresql://localhost:5432/modules",
                    username = "registry_user",
                    password = "registry_pwd",
                ),
            ),
            storageDir = Files.createTempDirectory("ui-db-output-retention-state-").toString(),
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
        val retentionService = object : DatabaseOutputRetentionService(
            runStore = DatabaseRunStore(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
        ) {
            override fun previewCleanup(disableSafeguard: Boolean) =
                com.sbrf.lt.platform.ui.model.output.OutputRetentionPreviewResponse(
                    storageMode = "DATABASE",
                    safeguardEnabled = !disableSafeguard,
                    retentionDays = 14,
                    keepMinRunsPerModule = 20,
                    cutoffTimestamp = Instant.parse("2026-04-01T00:00:00Z"),
                    totalModulesAffected = 1,
                    totalRunsAffected = 3,
                    totalOutputDirsToDelete = 2,
                    totalMissingOutputDirs = 1,
                    totalBytesToFree = 1024L * 1024L,
                    modules = listOf(
                        com.sbrf.lt.platform.ui.model.output.OutputRetentionModuleResponse(
                            moduleCode = "alpha",
                            totalRunsAffected = 3,
                            totalOutputDirsToDelete = 2,
                            totalBytesToFree = 1024L * 1024L,
                            oldestRequestedAt = Instant.parse("2026-03-01T00:00:00Z"),
                            newestRequestedAt = Instant.parse("2026-03-10T00:00:00Z"),
                        ),
                    ),
                )

            override fun executeCleanup(disableSafeguard: Boolean) =
                com.sbrf.lt.platform.ui.model.output.OutputRetentionResultResponse(
                    storageMode = "DATABASE",
                    safeguardEnabled = !disableSafeguard,
                    retentionDays = 14,
                    keepMinRunsPerModule = 20,
                    cutoffTimestamp = Instant.parse("2026-04-01T00:00:00Z"),
                    finishedAt = Instant.parse("2026-04-19T12:00:00Z"),
                    totalModulesAffected = 1,
                    totalRunsAffected = 3,
                    totalOutputDirsDeleted = 2,
                    totalMissingOutputDirs = 1,
                    totalBytesFreed = 1024L * 1024L,
                    modules = listOf(
                        com.sbrf.lt.platform.ui.model.output.OutputRetentionModuleResponse(
                            moduleCode = "alpha",
                            totalRunsAffected = 3,
                            totalOutputDirsToDelete = 2,
                            totalBytesToFree = 1024L * 1024L,
                        ),
                    ),
                )
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                databaseOutputRetentionService = retentionService,
            )
        }

        val previewResponse = client.get("/api/output-retention/preview?disableSafeguard=true")
        val cleanupResponse = client.post("/api/output-retention") {
            contentType(ContentType.Application.Json)
            setBody("""{"disableSafeguard":true}""")
        }

        assertEquals(HttpStatusCode.OK, previewResponse.status)
        assertTrue(previewResponse.bodyAsText().contains("\"storageMode\":\"DATABASE\""))
        assertTrue(previewResponse.bodyAsText().contains("\"totalOutputDirsToDelete\":2"))
        assertTrue(previewResponse.bodyAsText().contains("\"totalBytesToFree\":1048576"))

        assertEquals(HttpStatusCode.OK, cleanupResponse.status)
        assertTrue(cleanupResponse.bodyAsText().contains("\"storageMode\":\"DATABASE\""))
        assertTrue(cleanupResponse.bodyAsText().contains("\"totalOutputDirsDeleted\":2"))
        assertTrue(cleanupResponse.bodyAsText().contains("\"totalBytesFreed\":1048576"))
    }

    @Test
    fun `common output retention endpoints work in files mode`() = testApplication {
        val storageDir = Files.createTempDirectory("ui-files-output-retention-state-")
        val outputRoot = Files.createTempDirectory("ui-files-output-retention-out-")
        val now = Instant.now()
        val firstDir = outputRoot.resolve("run-1").apply {
            Files.createDirectories(this)
            resolve("merged.csv").writeText("id\n1\n")
        }
        val secondDir = outputRoot.resolve("run-2").apply {
            Files.createDirectories(this)
            resolve("merged.csv").writeText("id\n2\n")
        }
        RunStateStore(storageDir).save(
            PersistedRunState(
                history = buildList {
                    repeat(20) { index ->
                        add(
                            com.sbrf.lt.platform.ui.model.UiRunSnapshot(
                                id = "keep-$index",
                                moduleId = "files-alpha",
                                moduleTitle = "Files Alpha",
                                status = com.sbrf.lt.datapool.model.ExecutionStatus.SUCCESS,
                                startedAt = now.minusSeconds((31L + index) * 86_400),
                                finishedAt = now.minusSeconds((31L + index) * 86_400).plusSeconds(60),
                                outputDir = outputRoot.resolve("keep-$index").toString(),
                            ),
                        )
                    }
                    add(
                        com.sbrf.lt.platform.ui.model.UiRunSnapshot(
                            id = "delete-1",
                            moduleId = "files-alpha",
                            moduleTitle = "Files Alpha",
                            status = com.sbrf.lt.datapool.model.ExecutionStatus.SUCCESS,
                            startedAt = now.minusSeconds(80L * 86_400),
                            finishedAt = now.minusSeconds(80L * 86_400).plusSeconds(60),
                            outputDir = firstDir.toString(),
                        ),
                    )
                    add(
                        com.sbrf.lt.platform.ui.model.UiRunSnapshot(
                            id = "delete-2",
                            moduleId = "files-alpha",
                            moduleTitle = "Files Alpha",
                            status = com.sbrf.lt.datapool.model.ExecutionStatus.SUCCESS,
                            startedAt = now.minusSeconds(81L * 86_400),
                            finishedAt = now.minusSeconds(81L * 86_400).plusSeconds(60),
                            outputDir = secondDir.toString(),
                        ),
                    )
                },
            ),
        )
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.FILES),
            storageDir = storageDir.toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        application {
            uiModule(
                uiConfig = uiConfig,
                runManager = RunManager(uiConfig = uiConfig),
            )
        }

        val previewResponse = client.get("/api/output-retention/preview")
        val cleanupResponse = client.post("/api/output-retention") {
            contentType(ContentType.Application.Json)
            setBody("""{"disableSafeguard":false}""")
        }

        assertEquals(HttpStatusCode.OK, previewResponse.status)
        assertTrue(previewResponse.bodyAsText().contains("\"storageMode\":\"FILES\""))
        assertTrue(previewResponse.bodyAsText().contains("\"totalOutputDirsToDelete\":2"))

        assertEquals(HttpStatusCode.OK, cleanupResponse.status)
        assertTrue(cleanupResponse.bodyAsText().contains("\"storageMode\":\"FILES\""))
        assertTrue(cleanupResponse.bodyAsText().contains("\"totalOutputDirsDeleted\":2"))
        assertFalse(Files.exists(firstDir))
        assertFalse(Files.exists(secondDir))
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
                        descriptor = com.sbrf.lt.platform.ui.model.ModuleMetadataDescriptorResponse(
                            title = "DB Demo",
                        ),
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
    fun `db module details endpoint returns not found for unknown module`() = testApplication {
        val uiConfig = UiAppConfig(
            moduleStore = UiModuleStoreConfig(mode = UiModuleStoreMode.DATABASE),
            storageDir = Files.createTempDirectory("ui-db-module-details-not-found-state-").toString(),
            sqlConsole = SqlConsoleConfig(),
        )
        val runtimeContextService = object : UiRuntimeContextService() {
            override fun resolve(uiConfig: UiAppConfig): UiRuntimeContext =
                testRuntimeContext(UiModuleStoreMode.DATABASE)
        }
        val databaseModuleStore = object : DatabaseModuleStore(
            connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
        ) {
            override fun loadModuleDetails(
                moduleCode: String,
                actorId: String,
                actorSource: String,
            ): DatabaseEditableModule = throw com.sbrf.lt.platform.ui.module.DatabaseModuleNotFoundException(moduleCode)
        }
        application {
            uiModule(
                uiConfig = uiConfig,
                runtimeContextService = runtimeContextService,
                databaseModuleStore = databaseModuleStore,
            )
        }

        val detailsResponse = client.get("/api/db/modules/unknown")

        assertEquals(HttpStatusCode.NotFound, detailsResponse.status)
        assertTrue(detailsResponse.bodyAsText().contains("DB-модуль 'unknown' не найден."))
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
            runExecutionStore = DatabaseRunStore(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
            runQueryStore = DatabaseRunStore(
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
            runExecutionStore = DatabaseRunStore(
                connectionProvider = DatabaseConnectionProvider { error("connection must not be requested") },
            ),
            runQueryStore = DatabaseRunStore(
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
        assertEquals(HttpStatusCode.NotFound, invalidModuleResponse.status)
        assertTrue(invalidModuleResponse.bodyAsText().contains("не найден"))

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

}
