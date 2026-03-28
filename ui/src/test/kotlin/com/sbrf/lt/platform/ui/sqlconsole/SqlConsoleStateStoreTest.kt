package com.sbrf.lt.platform.ui.sqlconsole

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlConsoleStateStoreTest {

    @Test
    fun `saves and loads sql console state in separate file`() {
        val storageDir = Files.createTempDirectory("sql-console-state")
        val store = SqlConsoleStateStore(storageDir)

        store.save(
            PersistedSqlConsoleState(
                draftSql = "select * from demo",
                recentQueries = listOf("select * from demo", "select * from demo", "select 1"),
                selectedSourceNames = listOf("db1", "db1", "db2"),
                pageSize = 100,
            )
        )

        val loaded = store.load()

        assertEquals("select * from demo", loaded.draftSql)
        assertEquals(listOf("select * from demo", "select 1"), loaded.recentQueries)
        assertEquals(listOf("db1", "db2"), loaded.selectedSourceNames)
        assertEquals(100, loaded.pageSize)
        assertTrue(Files.exists(storageDir.resolve("sql-console-state.json")))
    }
}
