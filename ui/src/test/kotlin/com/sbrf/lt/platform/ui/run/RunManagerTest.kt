package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.datapool.db.PostgresSourceExporter
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.model.StartRunRequest
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Statement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.nio.file.Files
import kotlin.test.assertFalse

class RunManagerTest {

    @Test
    fun `uploaded credentials override fallback file`() {
        val fallbackFile = createTempDirectory("datapool-ui-fallback-")
            .resolve("credential.properties")
            .apply { writeText("DB1_USERNAME=fallback") }

        val runManager = RunManager(
            uiConfig = UiAppConfig(
                defaultCredentialsFile = fallbackFile.toString(),
                storageDir = createTempDirectory("datapool-ui-storage-").toString(),
            ),
        )
        runManager.uploadCredentials(
            fileName = "uploaded.properties",
            content = "DB1_USERNAME=uploaded",
        )

        val materialized = runManager.materializeCredentialsFile(createTempDirectory("datapool-ui-run-"))

        assertNotNull(materialized)
        assertEquals("DB1_USERNAME=uploaded", materialized.readText())
    }

    @Test
    fun `restores uploaded credentials and history from storage`() {
        val projectRoot = createProject()
        val storageDir = createTempDirectory("datapool-ui-storage-")
        val registry = ModuleRegistry(appsRoot = projectRoot.resolve("apps"))
        val uiConfig = UiAppConfig(storageDir = storageDir.toString())
        val firstRunManager = RunManager(
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

        firstRunManager.uploadCredentials("uploaded.properties", "DB1_USERNAME=uploaded")
        firstRunManager.startRun(
            StartRunRequest(
                moduleId = "demo-app",
                configText = registry.loadModuleDetails("demo-app").configText,
                sqlFiles = mapOf("classpath:sql/common.sql" to "select 1"),
            ),
        )
        waitForCompletion(firstRunManager)

        val restoredRunManager = RunManager(
            moduleRegistry = registry,
            uiConfig = uiConfig,
        )

        val restoredState = restoredRunManager.currentState()
        assertEquals("UPLOADED", restoredState.credentialsStatus.mode)
        assertEquals("uploaded.properties", restoredState.credentialsStatus.displayName)
        assertTrue(restoredState.history.isNotEmpty())
        assertEquals(ExecutionStatus.SUCCESS, restoredState.history.first().status)
        assertTrue(restoredState.history.first().events.isNotEmpty())
    }

    @Test
    fun `restored running run is marked as failed after restart`() {
        val storageDir = createTempDirectory("datapool-ui-storage-")
        RunStateStore(storageDir).save(
            PersistedRunState(
                history = listOf(
                    com.sbrf.lt.platform.ui.model.UiRunSnapshot(
                        id = "run-1",
                        moduleId = "demo-app",
                        moduleTitle = "Demo App",
                        status = ExecutionStatus.RUNNING,
                        startedAt = java.time.Instant.parse("2026-03-28T00:00:00Z"),
                    ),
                ),
            ),
        )

        val runManager = RunManager(
            uiConfig = UiAppConfig(storageDir = storageDir.toString()),
        )

        val restored = runManager.currentState().history.first()
        assertEquals(ExecutionStatus.FAILED, restored.status)
        assertFalse(restored.errorMessage.isNullOrBlank())
        assertNotNull(restored.finishedAt)
    }

    @Test
    fun `fallback file is used when nothing uploaded`() {
        val fallbackFile = createTempDirectory("datapool-ui-fallback-")
            .resolve("credential.properties")
            .apply { writeText("DB1_USERNAME=fallback") }

        val runManager = RunManager(
            uiConfig = UiAppConfig(
                defaultCredentialsFile = fallbackFile.toString(),
                storageDir = createTempDirectory("datapool-ui-storage-").toString(),
            ),
        )

        val materialized = runManager.materializeCredentialsFile(createTempDirectory("datapool-ui-run-"))

        assertEquals(fallbackFile, materialized)
    }

    @Test
    fun `materialize credentials returns null when nothing configured`() {
        val runManager = RunManager(
            uiConfig = UiAppConfig(storageDir = createTempDirectory("datapool-ui-storage-").toString()),
        )

        val materialized = runManager.materializeCredentialsFile(createTempDirectory("datapool-ui-run-"))

        assertEquals(null, materialized)
    }

    @Test
    fun `updates flow replays current state`() = runBlocking {
        val runManager = RunManager(
            uiConfig = UiAppConfig(storageDir = createTempDirectory("datapool-ui-storage-").toString()),
        )

        val state = runManager.updates().first()

        assertEquals(runManager.currentState(), state)
    }

    @Test
    fun `starts run and stores successful result in history`() {
        val projectRoot = createProject()
        val registry = ModuleRegistry(appsRoot = projectRoot.resolve("apps"))
        val runManager = RunManager(
            moduleRegistry = registry,
            applicationRunner = ApplicationRunner(
                exporter = PostgresSourceExporter { _, _, _ ->
                    exportConnection(
                        columns = listOf("id", "name"),
                        rows = listOf(listOf(1, "A")),
                    )
                },
            ),
            uiConfig = UiAppConfig(storageDir = createTempDirectory("datapool-ui-storage-").toString()),
        )

        runManager.startRun(
            StartRunRequest(
                moduleId = "demo-app",
                configText = registry.loadModuleDetails("demo-app").configText,
                sqlFiles = mapOf("classpath:sql/common.sql" to "select 1"),
            ),
        )

        waitForCompletion(runManager)
        val state = runManager.currentState()
        val latest = state.history.first()
        assertEquals(ExecutionStatus.SUCCESS, latest.status)
        assertEquals(1L, latest.mergedRowCount)
        assertTrue(latest.summaryJson?.contains("\"mergedRowCount\" : 1") == true)
    }

    @Test
    fun `starts run with relative sql file reference`() {
        val projectRoot = createProject(
            configText = """
                app:
                  outputDir: ./output
                  mergeMode: plain
                  errorMode: continue_on_error
                  parallelism: 1
                  fetchSize: 100
                  commonSqlFile: ./sql/common.sql
                  target:
                    enabled: false
                  sources:
                    - name: db1
                      jdbcUrl: jdbc:test:db1
                      username: user
                      password: pwd
            """.trimIndent(),
        )
        val registry = ModuleRegistry(appsRoot = projectRoot.resolve("apps"))
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
            uiConfig = UiAppConfig(storageDir = createTempDirectory("datapool-ui-storage-").toString()),
        )

        runManager.startRun(
            StartRunRequest(
                moduleId = "demo-app",
                configText = registry.loadModuleDetails("demo-app").configText,
                sqlFiles = mapOf("./sql/common.sql" to "select 7"),
            ),
        )

        waitForCompletion(runManager)
        assertEquals(ExecutionStatus.SUCCESS, runManager.currentState().history.first().status)
    }

    @Test
    fun `load module details reflects placeholder requirement and credentials status`() {
        val projectRoot = createProject(
            configText = """
                app:
                  outputDir: ./output
                  mergeMode: plain
                  errorMode: continue_on_error
                  parallelism: 1
                  fetchSize: 100
                  commonSql: select 1
                  target:
                    enabled: false
                  sources:
                    - name: db1
                      jdbcUrl: ${'$'}{DB1_JDBC_URL}
                      username: ${'$'}{DB1_USERNAME}
                      password: ${'$'}{DB1_PASSWORD}
            """.trimIndent(),
        )
        val fallbackFile = createTempDirectory("datapool-ui-fallback-")
            .resolve("credential.properties")
            .apply { writeText("DB1_USERNAME=fallback") }
        val registry = ModuleRegistry(appsRoot = projectRoot.resolve("apps"))
        val runManager = RunManager(
            moduleRegistry = registry,
            uiConfig = UiAppConfig(
                defaultCredentialsFile = fallbackFile.toString(),
                storageDir = createTempDirectory("datapool-ui-storage-").toString(),
            ),
        )

        val details = runManager.loadModuleDetails("demo-app")

        assertTrue(details.requiresCredentials)
        assertEquals("FILE", details.credentialsStatus.mode)
        assertTrue(details.credentialsStatus.fileAvailable)
        assertFalse(details.credentialsReady)
        assertEquals(listOf("DB1_JDBC_URL", "DB1_USERNAME", "DB1_PASSWORD"), details.requiredCredentialKeys)
        assertEquals(listOf("DB1_JDBC_URL", "DB1_PASSWORD"), details.missingCredentialKeys)
    }

    @Test
    fun `rejects concurrent run start and records failed run when runner throws`() {
        val projectRoot = createProject()
        val registry = ModuleRegistry(appsRoot = projectRoot.resolve("apps"))
        val runManager = RunManager(
            moduleRegistry = registry,
            applicationRunner = ApplicationRunner(
                exporter = PostgresSourceExporter { _, _, _ ->
                    Thread.sleep(400)
                    error("runner boom")
                },
            ),
            uiConfig = UiAppConfig(storageDir = createTempDirectory("datapool-ui-storage-").toString()),
        )

        runManager.startRun(
            StartRunRequest(
                moduleId = "demo-app",
                configText = registry.loadModuleDetails("demo-app").configText,
                sqlFiles = emptyMap(),
            ),
        )

        val concurrentError = assertFailsWith<IllegalArgumentException> {
            runManager.startRun(
                StartRunRequest(
                    moduleId = "demo-app",
                    configText = registry.loadModuleDetails("demo-app").configText,
                    sqlFiles = emptyMap(),
                ),
            )
        }
        assertTrue(concurrentError.message!!.contains("Уже выполняется другой запуск"))

        waitForCompletion(runManager)
        val latest = runManager.currentState().history.first()
        assertEquals(ExecutionStatus.FAILED, latest.status)
        assertTrue(latest.errorMessage!!.contains("Файл merged.csv не был создан"))
    }

    @Test
    fun `rejects run when placeholders are present and credentials are missing`() {
        val projectRoot = createProject(
            configText = """
                app:
                  outputDir: ./output
                  mergeMode: plain
                  errorMode: continue_on_error
                  parallelism: 1
                  fetchSize: 100
                  commonSql: select 1
                  target:
                    enabled: false
                  sources:
                    - name: db1
                      jdbcUrl: ${'$'}{DB1_JDBC_URL}
                      username: ${'$'}{DB1_USERNAME}
                      password: ${'$'}{DB1_PASSWORD}
            """.trimIndent(),
        )
        val registry = ModuleRegistry(appsRoot = projectRoot.resolve("apps"))
        val previous = System.getProperty("credentials.file")
        try {
            System.setProperty("credentials.file", projectRoot.resolve("missing-credentials.properties").toString())
            val runManager = RunManager(
                moduleRegistry = registry,
                uiConfig = UiAppConfig(storageDir = createTempDirectory("datapool-ui-storage-").toString()),
            )

            val error = assertFailsWith<IllegalArgumentException> {
                runManager.startRun(
                    StartRunRequest(
                        moduleId = "demo-app",
                        configText = registry.loadModuleDetails("demo-app").configText,
                        sqlFiles = emptyMap(),
                    ),
                )
            }

            assertTrue(error.message!!.contains("credential.properties"))
            assertTrue(error.message!!.contains("DB1_JDBC_URL"))
        } finally {
            if (previous != null) {
                System.setProperty("credentials.file", previous)
            } else {
                System.clearProperty("credentials.file")
            }
        }
    }

    @Test
    fun `rejects run when credentials file exists but required keys are missing`() {
        val projectRoot = createProject(
            configText = """
                app:
                  outputDir: ./output
                  mergeMode: plain
                  errorMode: continue_on_error
                  parallelism: 1
                  fetchSize: 100
                  commonSql: select 1
                  target:
                    enabled: false
                  sources:
                    - name: db1
                      jdbcUrl: ${'$'}{DB1_JDBC_URL}
                      username: ${'$'}{DB1_USERNAME}
                      password: ${'$'}{DB1_PASSWORD}
            """.trimIndent(),
        )
        val fallbackFile = createTempDirectory("datapool-ui-fallback-")
            .resolve("credential.properties")
            .apply { writeText("DB1_USERNAME=fallback") }
        val registry = ModuleRegistry(appsRoot = projectRoot.resolve("apps"))
        val runManager = RunManager(
            moduleRegistry = registry,
            uiConfig = UiAppConfig(
                defaultCredentialsFile = fallbackFile.toString(),
                storageDir = createTempDirectory("datapool-ui-storage-").toString(),
            ),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            runManager.startRun(
                StartRunRequest(
                    moduleId = "demo-app",
                    configText = registry.loadModuleDetails("demo-app").configText,
                    sqlFiles = emptyMap(),
                ),
            )
        }

        assertTrue(error.message!!.contains("DB1_JDBC_URL"))
        assertTrue(error.message!!.contains("DB1_PASSWORD"))
        assertTrue(error.message!!.contains("credential.properties"))
    }

    @Test
    fun `rejects empty uploaded credentials file`() {
        val runManager = RunManager(
            uiConfig = UiAppConfig(storageDir = createTempDirectory("datapool-ui-storage-").toString()),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            runManager.uploadCredentials("credential.properties", "   ")
        }

        assertEquals("Файл credential.properties пуст.", error.message)
    }

    private fun waitForCompletion(runManager: RunManager) {
        repeat(50) {
            val latest = runManager.currentState().history.firstOrNull()
            if (latest != null && latest.status != ExecutionStatus.RUNNING) {
                return
            }
            Thread.sleep(100)
        }
        error("RunManager did not finish in time")
    }

    private fun createProject(
        configText: String = """
            app:
              outputDir: ./output
              mergeMode: plain
              errorMode: continue_on_error
              parallelism: 1
              fetchSize: 100
              commonSqlFile: classpath:sql/common.sql
              target:
                enabled: false
              sources:
                - name: db1
                  jdbcUrl: jdbc:test:db1
                  username: user
                  password: pwd
        """.trimIndent(),
    ) = Files.createTempDirectory("run-manager").apply {
        resolve("settings.gradle.kts").writeText("rootProject.name = \"test\"")
        val appDir = resolve("apps/demo-app")
        Files.createDirectories(appDir)
        appDir.resolve("build.gradle.kts").writeText("plugins { application }")
        val resources = appDir.resolve("src/main/resources")
        Files.createDirectories(resources)
        resources.resolve("application.yml").writeText(configText)
        Files.createDirectories(resources.resolve("sql"))
        resources.resolve("sql/common.sql").writeText("select 1")
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
}
