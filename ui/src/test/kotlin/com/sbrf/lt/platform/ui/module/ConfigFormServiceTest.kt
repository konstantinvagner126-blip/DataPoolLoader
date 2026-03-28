package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.platform.ui.model.ConfigFormQuotaState
import com.sbrf.lt.platform.ui.model.ConfigFormSourceState
import com.sbrf.lt.platform.ui.model.ConfigFormStateResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigFormServiceTest {

    private val service = ConfigFormService()

    @Test
    fun `parses config with placeholders into form state`() {
        val state = service.parse(
            """
            app:
              outputDir: ./output
              mergeMode: round_robin
              parallelism: 5
              fetchSize: 1000
              queryTimeoutSec: 60
              progressLogEveryRows: 10000
              maxMergedRows:
              deleteOutputFilesAfterCompletion: false
              sources:
                - name: db1
                  jdbcUrl: ${'$'}{DB1_JDBC_URL}
                  username: ${'$'}{DB1_USERNAME}
                  password: ${'$'}{DB1_PASSWORD}
              target:
                enabled: true
                jdbcUrl: ${'$'}{TARGET_JDBC_URL}
                username: ${'$'}{TARGET_USERNAME}
                password: ${'$'}{TARGET_PASSWORD}
                table: public.test_data_pool
                truncateBeforeLoad: true
            """.trimIndent(),
        )

        assertEquals("./output", state.outputDir)
        assertEquals("csv", state.fileFormat)
        assertEquals("round_robin", state.mergeMode)
        assertEquals("continue_on_error", state.errorMode)
        assertEquals(5, state.parallelism)
        assertEquals(1000, state.fetchSize)
        assertEquals(60, state.queryTimeoutSec)
        assertEquals(10_000, state.progressLogEveryRows)
        assertEquals(null, state.maxMergedRows)
        assertFalse(state.deleteOutputFilesAfterCompletion)
        assertEquals("", state.commonSql)
        assertEquals(1, state.sources.size)
        assertEquals("${'$'}{DB1_JDBC_URL}", state.sources.first().jdbcUrl)
        assertTrue(state.targetEnabled)
        assertEquals("${'$'}{TARGET_JDBC_URL}", state.targetJdbcUrl)
        assertEquals("public.test_data_pool", state.targetTable)
        assertTrue(state.targetTruncateBeforeLoad)
    }

    @Test
    fun `applies visual form changes without dropping unsupported config blocks`() {
        val result = service.apply(
            configText = """
                app:
                  outputDir: ./output
                  mergeMode: plain
                  commonSqlFile: classpath:sql/common.sql
                  sources:
                    - name: db1
                      jdbcUrl: ${'$'}{DB1_JDBC_URL}
                      username: ${'$'}{DB1_USERNAME}
                      password: ${'$'}{DB1_PASSWORD}
                  target:
                    enabled: false
                    jdbcUrl: ${'$'}{TARGET_JDBC_URL}
                    username: ${'$'}{TARGET_USERNAME}
                    password: ${'$'}{TARGET_PASSWORD}
                    table: public.old_table
                    truncateBeforeLoad: false
            """.trimIndent(),
            formState = ConfigFormStateResponse(
                outputDir = "./custom-output",
                fileFormat = "csv",
                mergeMode = "quota",
                errorMode = "continue_on_error",
                parallelism = 7,
                fetchSize = 2000,
                queryTimeoutSec = 90,
                progressLogEveryRows = 5000,
                maxMergedRows = 12345,
                deleteOutputFilesAfterCompletion = true,
                commonSql = "select 1 as id",
                commonSqlFile = "classpath:sql/override.sql",
                sources = listOf(
                    ConfigFormSourceState(
                        name = "db1",
                        jdbcUrl = "${'$'}{DB1_JDBC_URL}",
                        username = "${'$'}{DB1_USERNAME}",
                        password = "${'$'}{DB1_PASSWORD}",
                        sql = "select 42",
                        sqlFile = "classpath:sql/db1.sql",
                    ),
                ),
                quotas = listOf(
                    ConfigFormQuotaState(
                        source = "db1",
                        percent = 100.0,
                    ),
                ),
                targetEnabled = true,
                targetJdbcUrl = "${'$'}{TARGET_JDBC_URL}",
                targetUsername = "${'$'}{TARGET_USERNAME}",
                targetPassword = "${'$'}{TARGET_PASSWORD}",
                targetTable = "public.new_table",
                targetTruncateBeforeLoad = true,
            ),
        )

        assertTrue(result.configText.contains("commonSqlFile: \"classpath:sql/override.sql\""))
        assertTrue(result.configText.contains("sources:"))
        assertTrue(result.configText.contains("mergeMode: \"quota\""))
        assertTrue(result.configText.contains("errorMode: \"continue_on_error\""))
        assertTrue(result.configText.contains("commonSql: \"select 1 as id\""))
        assertTrue(result.configText.contains("sqlFile: \"classpath:sql/db1.sql\""))
        assertTrue(result.configText.contains("quotas:"))
        assertTrue(result.configText.contains("parallelism: 7"))
        assertTrue(result.configText.contains("table: \"public.new_table\""))
        assertEquals("quota", result.formState.mergeMode)
        assertEquals(12345, result.formState.maxMergedRows)
        assertEquals(1, result.formState.sources.size)
        assertEquals(1, result.formState.quotas.size)
        assertTrue(result.formState.targetEnabled)
    }

    @Test
    fun `parses all real module configs in repository`() {
        val configFiles = listOf(
            projectPath("apps/dc-sms-offer/src/main/resources/application.yml"),
            projectPath("apps/local-manual-test/src/main/resources/application.yml"),
            projectPath("apps/local-manual-big-test/src/main/resources/application.yml"),
            projectPath("templates/app-module/src/main/resources/application.yml"),
        )

        configFiles.forEach { path ->
            val state = service.parse(Files.readString(path))
            assertTrue(state.outputDir.isNotBlank(), "outputDir should be parsed for $path")
            assertTrue(state.mergeMode.isNotBlank(), "mergeMode should be parsed for $path")
        }
    }

    private fun projectPath(relative: String): Path = Path.of("")
        .toAbsolutePath()
        .normalize()
        .let { start ->
            generateSequence(start) { current -> current.parent }
                .firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
                ?.resolve(relative)
                ?: error("Project root was not found for test path resolution.")
        }
}
