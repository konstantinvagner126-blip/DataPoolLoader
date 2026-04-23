package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStoreSqlResourceNamingSupportTest {
    private val support = ModuleEditorStoreSqlResourceNamingSupport()

    @Test
    fun `normalizes simple SQL resource names into classpath path`() {
        assertEquals(
            "classpath:sql/report-main.sql",
            support.normalizeSqlResourceKey(" Report Main "),
        )
    }

    @Test
    fun `preserves explicit classpath SQL resource path`() {
        assertEquals(
            "classpath:sql/existing-file.sql",
            support.normalizeSqlResourceKey("classpath:sql/existing-file.sql"),
        )
    }

    @Test
    fun `uses fallback resource key when normalized input becomes empty`() {
        assertEquals(
            "classpath:sql/current-name.sql",
            support.normalizeSqlResourceKey("!!!", "classpath:sql/current-name.sql"),
        )
    }

    @Test
    fun `sorts SQL contents by path`() {
        val sorted = support.sortSqlContents(
            linkedMapOf(
                "classpath:sql/z.sql" to "select 3",
                "classpath:sql/a.sql" to "select 1",
                "classpath:sql/m.sql" to "select 2",
            ),
        )

        assertEquals(
            listOf(
                "classpath:sql/a.sql",
                "classpath:sql/m.sql",
                "classpath:sql/z.sql",
            ),
            sorted.keys.toList(),
        )
    }
}
