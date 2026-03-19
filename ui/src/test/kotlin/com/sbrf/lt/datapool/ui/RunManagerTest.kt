package com.sbrf.lt.datapool.ui

import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.datapool.db.PostgresExporter
import com.sbrf.lt.datapool.model.ExecutionStatus
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.Statement
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.nio.file.Files

class RunManagerTest {

    @Test
    fun `uploaded credentials override fallback file`() {
        val fallbackFile = createTempDirectory("datapool-ui-fallback-")
            .resolve("credential.properties")
            .apply { writeText("DB1_USERNAME=fallback") }

        val runManager = RunManager(
            uiConfig = UiAppConfig(defaultCredentialsFile = fallbackFile.toString()),
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
    fun `fallback file is used when nothing uploaded`() {
        val fallbackFile = createTempDirectory("datapool-ui-fallback-")
            .resolve("credential.properties")
            .apply { writeText("DB1_USERNAME=fallback") }

        val runManager = RunManager(
            uiConfig = UiAppConfig(defaultCredentialsFile = fallbackFile.toString()),
        )

        val materialized = runManager.materializeCredentialsFile(createTempDirectory("datapool-ui-run-"))

        assertEquals(fallbackFile, materialized)
    }

    @Test
    fun `starts run and stores successful result in history`() {
        val projectRoot = createProject()
        val registry = ModuleRegistry(projectRoot = projectRoot)
        val runManager = RunManager(
            moduleRegistry = registry,
            applicationRunner = ApplicationRunner(
                exporter = PostgresExporter { _, _, _ ->
                    exportConnection(
                        columns = listOf("id", "name"),
                        rows = listOf(listOf(1, "A")),
                    )
                },
            ),
            uiConfig = UiAppConfig(),
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
        val registry = ModuleRegistry(projectRoot = projectRoot)
        val runManager = RunManager(moduleRegistry = registry, uiConfig = UiAppConfig())

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
    }

    @Test
    fun `rejects empty uploaded credentials file`() {
        val runManager = RunManager(uiConfig = UiAppConfig())

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
