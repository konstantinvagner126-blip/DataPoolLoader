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
}
