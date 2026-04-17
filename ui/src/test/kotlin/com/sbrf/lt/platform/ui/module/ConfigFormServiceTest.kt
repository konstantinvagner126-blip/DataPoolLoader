package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.platform.ui.model.ConfigFormQuotaState
import com.sbrf.lt.platform.ui.model.ConfigFormSourceState
import com.sbrf.lt.platform.ui.model.ConfigFormStateResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertTrue(state.warnings.isEmpty())
    }

    @Test
    fun `parse keeps form available when enum and numeric fields are invalid`() {
        val state = service.parse(
            """
            app:
              outputDir: ./output
              mergeMode: broken_mode
              parallelism: many
              fetchSize: 1000
              progressLogEveryRows: invalid
              sources:
                - name: db1
                  jdbcUrl: jdbc:test
                  username: user
                  password: pwd
              target:
                enabled: yep
                table: public.pool
            """.trimIndent(),
        )

        assertEquals("plain", state.mergeMode)
        assertEquals(5, state.parallelism)
        assertEquals(10_000, state.progressLogEveryRows)
        assertTrue(state.targetEnabled)
        assertTrue(state.warnings.any { it.contains("mergeMode") })
        assertTrue(state.warnings.any { it.contains("parallelism") })
        assertTrue(state.warnings.any { it.contains("progressLogEveryRows") })
        assertTrue(state.warnings.any { it.contains("app.target.enabled") })
    }

    @Test
    fun `parse keeps supported sections even when yaml structure is partially unsupported`() {
        val state = service.parse(
            """
            app:
              outputDir: ./custom-output
              sources: invalid
              quotas:
                - source: db1
                  percent: fifty
              target:
                jdbcUrl: jdbc:target
                username: loader
                password: secret
                table: public.pool
            """.trimIndent(),
        )

        assertEquals("./custom-output", state.outputDir)
        assertTrue(state.sources.isEmpty())
        assertEquals(1, state.quotas.size)
        assertEquals("db1", state.quotas.first().source)
        assertEquals(null, state.quotas.first().percent)
        assertEquals("jdbc:target", state.targetJdbcUrl)
        assertTrue(state.warnings.any { it.contains("app.sources") })
        assertTrue(state.warnings.any { it.contains("app.quotas[0].percent") })
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

    @Test
    fun `apply removes optional fields and quotas without percent`() {
        val result = service.apply(
            configText = """
                app:
                  outputDir: ./output
                  mergeMode: plain
                  errorMode: continue_on_error
                  commonSql: select 1
                  commonSqlFile: classpath:sql/common.sql
                  queryTimeoutSec: 60
                  maxMergedRows: 100
                  sources:
                    - name: db1
                      jdbcUrl: jdbc:test
                      username: user
                      password: pwd
                      sql: select 2
                      sqlFile: classpath:sql/db1.sql
                  quotas:
                    - source: db1
                      percent: 50.0
                  target:
                    enabled: true
                    jdbcUrl: jdbc:test
                    username: user
                    password: pwd
                    table: public.pool
                    truncateBeforeLoad: true
            """.trimIndent(),
            formState = ConfigFormStateResponse(
                outputDir = "./output",
                fileFormat = "csv",
                mergeMode = "plain",
                errorMode = "continue_on_error",
                parallelism = 1,
                fetchSize = 1000,
                queryTimeoutSec = null,
                progressLogEveryRows = 10000,
                maxMergedRows = null,
                deleteOutputFilesAfterCompletion = false,
                commonSql = "",
                commonSqlFile = null,
                sources = listOf(
                    ConfigFormSourceState(
                        name = "db1",
                        jdbcUrl = "jdbc:test",
                        username = "user",
                        password = "pwd",
                        sql = "",
                        sqlFile = null,
                    ),
                ),
                quotas = listOf(ConfigFormQuotaState(source = "db1", percent = null)),
                targetEnabled = false,
                targetJdbcUrl = "",
                targetUsername = "",
                targetPassword = "",
                targetTable = "",
                targetTruncateBeforeLoad = false,
            ),
        )

        assertFalse(result.configText.contains("commonSqlFile"))
        assertFalse(result.configText.contains("commonSql:"))
        assertFalse(result.configText.contains("queryTimeoutSec"))
        assertFalse(result.configText.contains("maxMergedRows"))
        assertFalse(result.configText.contains("sqlFile"))
        assertFalse(result.configText.contains("percent"))
    }

    @Test
    fun `apply rejects unknown merge mode and error mode`() {
        val baseFormState = ConfigFormStateResponse(
            outputDir = "./output",
            fileFormat = "csv",
            mergeMode = "plain",
            errorMode = "continue_on_error",
            parallelism = 1,
            fetchSize = 1000,
            queryTimeoutSec = null,
            progressLogEveryRows = 10000,
            maxMergedRows = null,
            deleteOutputFilesAfterCompletion = false,
            commonSql = "",
            commonSqlFile = null,
            sources = emptyList(),
            quotas = emptyList(),
            targetEnabled = false,
            targetJdbcUrl = "",
            targetUsername = "",
            targetPassword = "",
            targetTable = "",
            targetTruncateBeforeLoad = false,
        )

        val mergeError = assertFailsWith<IllegalArgumentException> {
            service.apply("app: {}", baseFormState.copy(mergeMode = "broken"))
        }
        assertTrue(mergeError.message!!.contains("mergeMode"))

        val errorModeError = assertFailsWith<IllegalArgumentException> {
            service.apply("app: {}", baseFormState.copy(errorMode = "broken"))
        }
        assertTrue(errorModeError.message!!.contains("errorMode"))
    }

    @Test
    fun `apply creates app root when yaml is not an object`() {
        val result = service.apply(
            configText = "[]",
            formState = ConfigFormStateResponse(
                outputDir = "./output",
                fileFormat = "csv",
                mergeMode = "plain",
                errorMode = "continue_on_error",
                parallelism = 1,
                fetchSize = 1000,
                queryTimeoutSec = null,
                progressLogEveryRows = 10000,
                maxMergedRows = null,
                deleteOutputFilesAfterCompletion = false,
                commonSql = "",
                commonSqlFile = null,
                sources = emptyList(),
                quotas = emptyList(),
                targetEnabled = false,
                targetJdbcUrl = "",
                targetUsername = "",
                targetPassword = "",
                targetTable = "",
                targetTruncateBeforeLoad = false,
            ),
        )

        assertTrue(result.configText.contains("app:"))
        assertEquals("./output", result.formState.outputDir)
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
