package com.sbrf.lt.platform.ui.sqlconsole

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.io.path.writeText

class SqlConsoleStateStoreTest {

    @Test
    fun `saves and loads sql console state in separate file`() {
        val storageDir = Files.createTempDirectory("sql-console-state")
        val store = SqlConsoleStateStore(storageDir)

        store.save(
            PersistedSqlConsoleState(
                draftSql = "select * from demo",
                recentQueries = listOf("select * from demo", "select * from demo", "select 1"),
                favoriteQueries = listOf("select * from demo", "select 2", "select 2"),
                selectedSourceNames = listOf("db1", "db1", "db2"),
                pageSize = 100,
                strictSafetyEnabled = true,
            )
        )

        val loaded = store.load()

        assertEquals("select * from demo", loaded.draftSql)
        assertEquals(listOf("select * from demo", "select 1"), loaded.recentQueries)
        assertEquals(listOf("select * from demo", "select 2"), loaded.favoriteQueries)
        assertEquals(listOf("db1", "db2"), loaded.selectedSourceNames)
        assertEquals(100, loaded.pageSize)
        assertTrue(loaded.strictSafetyEnabled)
        assertTrue(Files.exists(storageDir.resolve("sql-console-state.json")))
    }

    @Test
    fun `falls back to default state when persisted file is incompatible`() {
        val storageDir = Files.createTempDirectory("sql-console-state-invalid")
        storageDir.resolve("sql-console-state.json").writeText("draftSql: [broken")
        val store = SqlConsoleStateStore(storageDir)

        val loaded = store.load()

        assertEquals("select 1 as check_value", loaded.draftSql)
        assertEquals(emptyList(), loaded.recentQueries)
        assertEquals(emptyList(), loaded.favoriteQueries)
        assertEquals(emptyList(), loaded.selectedSourceNames)
        assertEquals(50, loaded.pageSize)
        assertEquals(false, loaded.strictSafetyEnabled)
    }
}
