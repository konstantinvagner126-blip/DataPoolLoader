package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionCancelledException
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.model.SqlConsoleQueryRequest
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
import io.ktor.server.testing.testApplication
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.nio.file.Files

class ServerTest {

    @Test
    fun `serves html and module api`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val uiConfig = UiAppConfig(
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
        )
        application {
            uiModule(moduleRegistry = registry, runManager = runManager, sqlConsoleService = sqlConsoleService)
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

        val catalog = client.get("/api/modules/catalog").bodyAsText()
        assertTrue(catalog.contains("\"mode\":\"READY\""))
        assertTrue(catalog.contains("Доступно модулей: 1"))
        assertTrue(catalog.contains("demo-app"))

        val details = client.get("/api/modules/demo-app").bodyAsText()
        assertTrue(details.contains("Общий SQL"))
        assertTrue(details.contains("Источник: db2"))

        val info = client.get("/api/sql-console/info").bodyAsText()
        assertTrue(info.contains("\"sourceNames\":[\"shard1\"]"))
        assertTrue(info.contains("\"queryTimeoutSec\":30"))

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
    }

    @Test
    fun `uploads credentials and saves module files`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(appsRoot = root.resolve("apps"))
        val runManager = RunManager(moduleRegistry = registry, uiConfig = UiAppConfig())
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
        val uiConfig = UiAppConfig(
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
        application {
            uiModule(
                uiConfig = UiAppConfig(),
                moduleRegistry = ModuleRegistry(appsRoot = null),
                runManager = RunManager(uiConfig = UiAppConfig()),
            )
        }

        val catalog = client.get("/api/modules/catalog").bodyAsText()
        assertTrue(catalog.contains("\"mode\":\"NOT_CONFIGURED\""))
        assertTrue(catalog.contains("Путь ui.appsRoot не задан"))
        assertTrue(catalog.contains("\"modules\":[]"))
    }

    private fun createProject() = Files.createTempDirectory("ui-server").apply {
        resolve("settings.gradle.kts").writeText("rootProject.name = \"test\"")
        val appDir = resolve("apps/demo-app")
        appDir.createDirectories()
        appDir.resolve("build.gradle.kts").writeText("plugins { application }")
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
