package com.sbrf.lt.datapool.ui

import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.ShardSqlExecutor
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
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
import kotlin.test.assertTrue
import java.nio.file.Files

class ServerTest {

    @Test
    fun `serves html and module api`() = testApplication {
        val root = createProject()
        val registry = ModuleRegistry(projectRoot = root)
        val uiConfig = UiAppConfig(
            sqlConsole = SqlConsoleConfig(
                sources = listOf(SqlConsoleSourceConfig("shard1", "jdbc:test:one", "user", "pwd")),
            ),
        )
        val runManager = RunManager(moduleRegistry = registry, uiConfig = uiConfig)
        val sqlConsoleService = SqlConsoleService(
            config = uiConfig.sqlConsole,
            executor = ShardSqlExecutor { shard, statement, _, _ ->
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

        val html = client.get("/").bodyAsText()
        assertTrue(html.contains("Управление пулами данных НТ"))

        val sqlConsoleHtml = client.get("/sql-console").bodyAsText()
        assertTrue(sqlConsoleHtml.contains("SQL-редактор"))

        val modules = client.get("/api/modules").bodyAsText()
        assertTrue(modules.contains("demo-app"))

        val details = client.get("/api/modules/demo-app").bodyAsText()
        assertTrue(details.contains("Общий SQL"))
        assertTrue(details.contains("Источник: db2"))

        val info = client.get("/api/sql-console/info").bodyAsText()
        assertTrue(info.contains("\"sourceNames\":[\"shard1\"]"))

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
        val registry = ModuleRegistry(projectRoot = root)
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
