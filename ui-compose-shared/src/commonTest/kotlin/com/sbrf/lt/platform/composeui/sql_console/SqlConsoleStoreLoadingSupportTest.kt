package com.sbrf.lt.platform.composeui.sql_console

import kotlin.test.Test
import kotlin.test.assertEquals

class SqlConsoleStoreLoadingSupportTest {

    @Test
    fun `console load restores selected group and manual inclusion for ungrouped source`() {
        val support = SqlConsoleStoreLoadingSupport(
            StubSqlConsoleApi(
                infoHandler = { sampleSqlConsoleInfo() },
                loadStateHandler = {
                    SqlConsoleStateSnapshot(
                    draftSql = "select * from demo",
                    selectedGroupNames = listOf("dev"),
                    selectedSourceNames = listOf("db1", "db2", "db3"),
                    pageSize = 100,
                    strictSafetyEnabled = true,
                    transactionMode = "transaction_per_shard",
                    )
                },
            ),
        )

        val state = runSuspend { support.load("workspace-a") }

        assertEquals(listOf("dev"), state.selectedGroupNames)
        assertEquals(listOf("db1", "db2", "db3"), state.selectedSourceNames)
        assertEquals(listOf("db3"), state.manuallyIncludedSourceNames)
        assertEquals(emptyList(), state.manuallyExcludedSourceNames)
        assertEquals(100, state.pageSize)
        assertEquals(true, state.strictSafetyEnabled)
        assertEquals("TRANSACTION_PER_SHARD", state.transactionMode)
        assertEquals("select * from demo", state.draftSql)
    }

    @Test
    fun `objects load restores manual exclusion and ignores unknown persisted source`() {
        val support = SqlConsoleObjectsStoreLoadingSupport(
            StubSqlConsoleApi(
                infoHandler = { sampleSqlConsoleInfo() },
                loadStateHandler = {
                    SqlConsoleStateSnapshot(
                    draftSql = "select * from demo",
                    selectedGroupNames = listOf("dev"),
                    selectedSourceNames = listOf("db1", "missing"),
                    )
                },
            ),
        )

        val state = runSuspend { support.load("workspace-b") }

        assertEquals(listOf("dev"), state.selectedGroupNames)
        assertEquals(listOf("db1"), state.selectedSourceNames)
        assertEquals(emptyList(), state.manuallyIncludedSourceNames)
        assertEquals(listOf("db2"), state.manuallyExcludedSourceNames)
    }
}
