package com.sbrf.lt.platform.composeui.sql_console

import kotlin.test.Test
import kotlin.test.assertEquals

class SqlConsoleStoreLibrarySupportTest {
    private val support = SqlConsoleStoreLibrarySupport()

    @Test
    fun `remember favorite query trims sql and moves it to front`() {
        val current = SqlConsolePageState(
            draftSql = "  select 42  ",
            favoriteQueries = listOf("select 1", "select 42", "select 7"),
        )

        val updated = support.rememberFavoriteQuery(current)

        assertEquals(listOf("select 42", "select 1", "select 7"), updated.favoriteQueries)
        assertEquals("Запрос добавлен в избранное.", updated.successMessage)
        assertEquals(null, updated.errorMessage)
    }

    @Test
    fun `remember favorite query rejects blank sql`() {
        val current = SqlConsolePageState(draftSql = "   ")

        val updated = support.rememberFavoriteQuery(current)

        assertEquals(emptyList(), updated.favoriteQueries)
        assertEquals("Сначала введи SQL-запрос.", updated.errorMessage)
        assertEquals(null, updated.successMessage)
    }

    @Test
    fun `apply execution history restores draft and exact source selection`() {
        val current = SqlConsolePageState(
            info = sampleSqlConsoleInfo(),
            draftSql = "select 1",
            selectedGroupNames = listOf("dev"),
            selectedSourceNames = listOf("db1", "db2"),
        )

        val updated = support.applyExecutionHistoryEntry(
            current = current,
            entry = SqlConsoleExecutionHistoryEntry(
                executionId = "exec-7",
                sql = "select * from restored_table",
                selectedSourceNames = listOf("db1", "db3"),
                status = "SUCCESS",
                startedAt = "2026-04-23T11:10:00Z",
            ),
        )

        assertEquals("select * from restored_table", updated.draftSql)
        assertEquals(listOf("db3", "db1"), updated.selectedSourceNames)
        assertEquals(listOf("Без группы"), updated.selectedGroupNames)
        assertEquals(listOf("db1"), updated.manuallyIncludedSourceNames)
        assertEquals(emptyList(), updated.manuallyExcludedSourceNames)
    }
}
