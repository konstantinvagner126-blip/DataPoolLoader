package com.sbrf.lt.platform.composeui.sql_console

import kotlin.test.Test
import kotlin.test.assertEquals

class SqlConsoleSourceGroupSelectionSupportTest {

    @Test
    fun `initial selection marks fully covered groups as selected`() {
        val groups = listOf(
            SqlConsoleSourceGroup("dev", listOf("db1", "db2")),
            SqlConsoleSourceGroup("ift", listOf("db2", "db3")),
        )

        val state = initializeSelectedSourceState(groups, selectedSourceNames = listOf("db1", "db2", "db3"))

        assertEquals(listOf("db1", "db2", "db3"), state.selectedSourceNames)
        assertEquals(listOf("dev", "ift"), state.selectedGroupNames)
        assertEquals(emptyList(), state.manuallyIncludedSourceNames)
        assertEquals(emptyList(), state.manuallyExcludedSourceNames)
    }

    @Test
    fun `disabling one overlapping group keeps sources from still selected group`() {
        val groups = listOf(
            SqlConsoleSourceGroup("dev", listOf("db1", "db2")),
            SqlConsoleSourceGroup("ift", listOf("db2", "db3")),
        )

        val state = toggleSelectedSourceGroupNames(
            groups = groups,
            currentSelectedGroupNames = listOf("dev", "ift"),
            currentSelectedSourceNames = listOf("db1", "db2", "db3"),
            manuallyIncludedSourceNames = emptyList(),
            manuallyExcludedSourceNames = emptyList(),
            group = groups.first(),
            enabled = false,
        )

        assertEquals(listOf("ift"), state.selectedGroupNames)
        assertEquals(listOf("db2", "db3"), state.selectedSourceNames)
    }

    @Test
    fun `manual deselection overrides group selection`() {
        val groups = listOf(
            SqlConsoleSourceGroup("dev", listOf("db1", "db2")),
        )

        val state = toggleSelectedSourceWithGroups(
            groups = groups,
            currentSelectedGroupNames = listOf("dev"),
            currentSelectedSourceNames = listOf("db1", "db2"),
            manuallyIncludedSourceNames = emptyList(),
            manuallyExcludedSourceNames = emptyList(),
            sourceName = "db2",
            enabled = false,
        )

        assertEquals(listOf("dev"), state.selectedGroupNames)
        assertEquals(listOf("db1"), state.selectedSourceNames)
        assertEquals(listOf("db2"), state.manuallyExcludedSourceNames)
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
