package com.sbrf.lt.platform.composeui.sql_console

import kotlin.test.Test
import kotlin.test.assertEquals

class SqlConsoleSourceGroupSelectionSupportTest {

    @Test
    fun `toggle selected source group adds all group sources`() {
        val updated = toggleSelectedSourceGroupNames(
            current = listOf("db3"),
            group = SqlConsoleSourceGroup("dev", listOf("db1", "db2")),
            enabled = true,
        )

        assertEquals(listOf("db3", "db1", "db2"), updated)
    }

    @Test
    fun `toggle selected source group removes all group sources`() {
        val updated = toggleSelectedSourceGroupNames(
            current = listOf("db1", "db2", "db3"),
            group = SqlConsoleSourceGroup("dev", listOf("db1", "db2")),
            enabled = false,
        )

        assertEquals(listOf("db3"), updated)
    }

    @Test
    fun `source group selection state is partial when only part of group is selected`() {
        val state = sourceGroupSelectionState(
            group = SqlConsoleSourceGroup("ift", listOf("db1", "db2", "db3")),
            selectedSourceNames = listOf("db2"),
        )

        assertEquals(SqlConsoleSourceGroupSelectionState.PARTIAL, state)
    }
}
