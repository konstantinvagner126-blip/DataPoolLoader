package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleEditorStoreConfigFormSqlResourceSupportTest {
    private val support = ModuleEditorStoreConfigFormSqlResourceSupport()

    @Test
    fun `builds SQL resource usages for common SQL and bound sources`() {
        val usages = support.buildSqlResourceUsages(sampleConfigFormState(), "classpath:sql/common.sql")

        assertEquals(
            listOf("SQL по умолчанию", "Источник: alpha"),
            usages,
        )
    }

    @Test
    fun `detects whether SQL resource is used by form`() {
        val formState = sampleConfigFormState()

        assertTrue(support.sqlResourceUsedByForm(formState, "classpath:sql/common.sql"))
        assertFalse(support.sqlResourceUsedByForm(formState, "classpath:sql/missing.sql"))
    }

    @Test
    fun `renames SQL resource in common SQL and source bindings`() {
        val renamed = support.renameSqlResourceInFormState(
            formState = sampleConfigFormState(),
            currentPath = "classpath:sql/common.sql",
            nextPath = "classpath:sql/renamed.sql",
        )

        assertEquals("classpath:sql/renamed.sql", renamed.commonSqlFile)
        assertEquals("classpath:sql/renamed.sql", renamed.sources.first().sqlFile)
        assertEquals("classpath:sql/secondary.sql", renamed.sources.last().sqlFile)
    }

    private fun sampleConfigFormState(): ConfigFormStateDto =
        ConfigFormStateDto(
            outputDir = "build/out",
            fileFormat = "csv",
            mergeMode = "append",
            errorMode = "stop",
            parallelism = 1,
            fetchSize = 1000,
            progressLogEveryRows = 1000,
            deleteOutputFilesAfterCompletion = false,
            commonSql = "select 1",
            commonSqlFile = "classpath:sql/common.sql",
            sources = listOf(
                ConfigFormSourceStateDto(
                    name = "alpha",
                    jdbcUrl = "jdbc:test://alpha",
                    username = "user",
                    password = "pass",
                    sqlFile = "classpath:sql/common.sql",
                ),
                ConfigFormSourceStateDto(
                    name = "beta",
                    jdbcUrl = "jdbc:test://beta",
                    username = "user",
                    password = "pass",
                    sqlFile = "classpath:sql/secondary.sql",
                ),
            ),
            targetEnabled = false,
            targetJdbcUrl = "",
            targetUsername = "",
            targetPassword = "",
            targetTable = "",
            targetTruncateBeforeLoad = false,
        )
}
