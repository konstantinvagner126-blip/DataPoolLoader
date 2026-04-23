package com.sbrf.lt.platform.composeui.sql_console

import kotlin.test.Test
import kotlin.test.assertEquals

class SqlConsoleStoreStateSupportTest {
    private val support = SqlConsoleStoreStateSupport()

    @Test
    fun `selecting overlapping source keeps it selected across all containing groups`() {
        val current = samplePageStateWithOverlappingGroups()

        val updated = support.updateSelectedSources(current, sourceName = "db2", enabled = true)

        assertEquals(listOf("db2"), updated.selectedSourceNames)
        assertEquals(emptyList(), updated.selectedGroupNames)
        assertEquals(SqlConsoleSourceGroupSelectionState.PARTIAL, sourceGroupSelectionState(updated.info!!.groups[0], updated.selectedSourceNames))
        assertEquals(SqlConsoleSourceGroupSelectionState.PARTIAL, sourceGroupSelectionState(updated.info!!.groups[1], updated.selectedSourceNames))
    }

    @Test
    fun `deselecting overlapping source clears it from all containing groups`() {
        val current = samplePageStateWithOverlappingGroups(
            selectedSourceNames = listOf("db2"),
        )

        val updated = support.updateSelectedSources(current, sourceName = "db2", enabled = false)

        assertEquals(emptyList(), updated.selectedSourceNames)
        assertEquals(emptyList(), updated.selectedGroupNames)
        assertEquals(SqlConsoleSourceGroupSelectionState.NONE, sourceGroupSelectionState(updated.info!!.groups[0], updated.selectedSourceNames))
        assertEquals(SqlConsoleSourceGroupSelectionState.NONE, sourceGroupSelectionState(updated.info!!.groups[1], updated.selectedSourceNames))
    }

    @Test
    fun `selecting one group marks overlapping group as partial through shared source`() {
        val current = samplePageStateWithOverlappingGroups()

        val updated = support.updateSelectedSourceGroup(current, group = updatedGroups().first(), enabled = true)

        assertEquals(listOf("db1", "db2"), updated.selectedSourceNames)
        assertEquals(listOf("dev"), updated.selectedGroupNames)
        assertEquals(SqlConsoleSourceGroupSelectionState.ALL, sourceGroupSelectionState(updated.info!!.groups[0], updated.selectedSourceNames))
        assertEquals(SqlConsoleSourceGroupSelectionState.PARTIAL, sourceGroupSelectionState(updated.info!!.groups[1], updated.selectedSourceNames))
    }

    @Test
    fun `disabling one selected overlapping group keeps shared source selected through remaining group`() {
        val current = samplePageStateWithOverlappingGroups(
            selectedSourceNames = listOf("db1", "db2", "db3"),
            selectedGroupNames = listOf("dev", "ift"),
        )

        val updated = support.updateSelectedSourceGroup(current, group = updatedGroups()[1], enabled = false)

        assertEquals(listOf("db1", "db2"), updated.selectedSourceNames)
        assertEquals(listOf("dev"), updated.selectedGroupNames)
        assertEquals(SqlConsoleSourceGroupSelectionState.ALL, sourceGroupSelectionState(updated.info!!.groups[0], updated.selectedSourceNames))
        assertEquals(SqlConsoleSourceGroupSelectionState.PARTIAL, sourceGroupSelectionState(updated.info!!.groups[1], updated.selectedSourceNames))
    }

    private fun samplePageStateWithOverlappingGroups(
        selectedSourceNames: List<String> = emptyList(),
        selectedGroupNames: List<String> = emptyList(),
    ): SqlConsolePageState =
        SqlConsolePageState(
            info = SqlConsoleInfo(
                configured = true,
                sourceCatalog = listOf(
                    SqlConsoleSourceCatalogEntry(name = "db1"),
                    SqlConsoleSourceCatalogEntry(name = "db2"),
                    SqlConsoleSourceCatalogEntry(name = "db3"),
                ),
                groups = updatedGroups(),
                maxRowsPerShard = 200,
                queryTimeoutSec = 30,
            ),
            selectedSourceNames = selectedSourceNames,
            selectedGroupNames = selectedGroupNames,
        )

    private fun updatedGroups(): List<SqlConsoleSourceGroup> =
        listOf(
            SqlConsoleSourceGroup(name = "dev", sources = listOf("db1", "db2")),
            SqlConsoleSourceGroup(name = "ift", sources = listOf("db2", "db3")),
        )
}
