package com.sbrf.lt.platform.composeui.sql_console

import kotlin.test.Test
import kotlin.test.assertEquals

class SqlConsoleSourceSettingsStoreTest {

    @Test
    fun `remove source removes matching group references`() {
        val store = SqlConsoleSourceSettingsStore(StubSqlConsoleApi())
        val state = SqlConsoleSourceSettingsPageState(
            loading = false,
            settings = SqlConsoleSourceSettings(
                sources = listOf(
                    SqlConsoleEditableSource(originalName = "db1", name = "db-renamed"),
                    SqlConsoleEditableSource(originalName = "db2", name = "db2"),
                ),
                groups = listOf(
                    SqlConsoleEditableSourceGroup(name = "main", sources = listOf("db1", "db2")),
                    SqlConsoleEditableSourceGroup(name = "manual", sources = listOf("db-renamed")),
                ),
            ),
            connectionsTestResult = SqlConsoleSourceSettingsConnectionsTestResponse(
                success = true,
                message = "ok",
            ),
        )

        val updated = store.removeSource(state, sourceIndex = 0)

        val settings = updated.settings!!
        assertEquals(listOf("db2"), settings.sources.map { it.name })
        assertEquals(listOf("db2"), settings.groups.first { it.name == "main" }.sources)
        assertEquals(emptyList(), settings.groups.first { it.name == "manual" }.sources)
        assertEquals(null, updated.connectionsTestResult)
    }
}
