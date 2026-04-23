package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.io.path.writeText

class LegacySqlConsoleStateStoreTest {

    @Test
    fun `migrates legacy combined sql console state into workspace library and preferences stores`() {
        val storageDir = Files.createTempDirectory("sql-console-state")
        val legacyState = LegacySqlConsoleState(
            draftSql = "select * from demo",
            recentQueries = listOf("select * from demo", "select * from demo", "select 1"),
            favoriteQueries = listOf("select * from demo", "select 2", "select 2"),
            favoriteObjects = listOf(
                PersistedSqlConsoleFavoriteObject("shard1", "public", "offer", "table"),
                PersistedSqlConsoleFavoriteObject("shard1", "public", "offer", "TABLE"),
                PersistedSqlConsoleFavoriteObject("shard2", "public", "offer_idx", "index", tableName = "offer"),
            ),
            selectedSourceNames = listOf("db1", "db1", "db2"),
            pageSize = 100,
            strictSafetyEnabled = true,
        )
        storageDir.resolve("sql-console-state.json").writeText(
            ConfigLoader().objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(legacyState),
        )

        val workspace = SqlConsoleWorkspaceStateStore(storageDir).load()
        assertTrue(Files.exists(storageDir.resolve("sql-console-state.json")))
        val library = SqlConsoleLibraryStateStore(storageDir).load()
        assertTrue(Files.exists(storageDir.resolve("sql-console-state.json")))
        val preferences = SqlConsolePreferencesStateStore(storageDir).load()

        assertEquals("select * from demo", workspace.draftSql)
        assertEquals(null, workspace.selectedGroupNames)
        assertEquals(listOf("db1", "db2"), workspace.selectedSourceNames)
        assertEquals(listOf("select * from demo", "select 1"), library.recentQueries)
        assertEquals(listOf("select * from demo", "select 2"), library.favoriteQueries)
        assertEquals(
            listOf(
                PersistedSqlConsoleFavoriteObject("shard1", "public", "offer", "TABLE"),
                PersistedSqlConsoleFavoriteObject("shard2", "public", "offer_idx", "INDEX", tableName = "offer"),
            ),
            library.favoriteObjects,
        )
        assertEquals(100, preferences.pageSize)
        assertTrue(preferences.strictSafetyEnabled)
        assertTrue(Files.exists(storageDir.resolve("sql-console-workspace-state.json")))
        assertTrue(Files.exists(storageDir.resolve("sql-console-library-state.json")))
        assertTrue(Files.exists(storageDir.resolve("sql-console-preferences-state.json")))
        assertFalse(Files.exists(storageDir.resolve("sql-console-state.json")))
    }

    @Test
    fun `migrates content from old split preferences file into dedicated library store`() {
        val storageDir = Files.createTempDirectory("sql-console-state-old-preferences")
        storageDir.resolve("sql-console-preferences-state.json").writeText(
            """
            {
              "recentQueries":["select * from offer","select * from offer","select 1"],
              "favoriteQueries":["select count(*) from offer"],
              "favoriteObjects":[
                {"sourceName":"db1","schemaName":"public","objectName":"offer","objectType":"table"}
              ],
              "pageSize":100,
              "strictSafetyEnabled":true,
              "transactionMode":"TRANSACTION_PER_SHARD"
            }
            """.trimIndent()
        )

        val library = SqlConsoleLibraryStateStore(storageDir).load()
        val preferences = SqlConsolePreferencesStateStore(storageDir).load()

        assertEquals(listOf("select * from offer", "select 1"), library.recentQueries)
        assertEquals(listOf("select count(*) from offer"), library.favoriteQueries)
        assertEquals(
            listOf(PersistedSqlConsoleFavoriteObject("db1", "public", "offer", "TABLE")),
            library.favoriteObjects,
        )
        assertEquals(100, preferences.pageSize)
        assertTrue(preferences.strictSafetyEnabled)
        assertEquals("TRANSACTION_PER_SHARD", preferences.transactionMode)
        assertTrue(Files.exists(storageDir.resolve("sql-console-library-state.json")))
    }

    @Test
    fun `falls back to defaults when new persisted sql console files are incompatible`() {
        val storageDir = Files.createTempDirectory("sql-console-state-invalid")
        storageDir.resolve("sql-console-workspace-state.json").writeText("draftSql: [broken")
        storageDir.resolve("sql-console-library-state.json").writeText("favoriteQueries: [broken")
        storageDir.resolve("sql-console-preferences-state.json").writeText("pageSize: [broken")

        val workspace = SqlConsoleWorkspaceStateStore(storageDir).load()
        val library = SqlConsoleLibraryStateStore(storageDir).load()
        val preferences = SqlConsolePreferencesStateStore(storageDir).load()

        assertEquals("select 1 as check_value", workspace.draftSql)
        assertEquals(null, workspace.selectedGroupNames)
        assertEquals(emptyList(), workspace.selectedSourceNames)
        assertEquals(emptyList(), library.recentQueries)
        assertEquals(emptyList(), library.favoriteQueries)
        assertEquals(emptyList(), library.favoriteObjects)
        assertEquals(50, preferences.pageSize)
        assertFalse(preferences.strictSafetyEnabled)
    }

    @Test
    fun `state service composes workspace and preferences into unified response`() {
        val storageDir = Files.createTempDirectory("sql-console-state-service")
        val service = SqlConsoleStateService(storageDir)

        val updated = service.updateState(
            com.sbrf.lt.platform.ui.model.SqlConsoleStateUpdateRequest(
                draftSql = "select * from offer",
                recentQueries = listOf("select * from offer"),
                favoriteQueries = listOf("select count(*) from offer"),
                favoriteObjects = listOf(
                    com.sbrf.lt.platform.ui.model.SqlConsoleFavoriteObjectResponse(
                        sourceName = "db1",
                        schemaName = "public",
                        objectName = "offer",
                        objectType = "TABLE",
                    ),
                ),
                selectedGroupNames = listOf("dev"),
                selectedSourceNames = listOf("db1"),
                pageSize = 100,
                strictSafetyEnabled = true,
                transactionMode = "TRANSACTION_PER_SHARD",
            ),
        )

        assertEquals("select * from offer", updated.draftSql)
        assertEquals(listOf("dev"), updated.selectedGroupNames)
        assertEquals(listOf("db1"), updated.selectedSourceNames)
        assertEquals(listOf("select * from offer"), updated.recentQueries)
        assertEquals(listOf("select count(*) from offer"), updated.favoriteQueries)
        assertEquals(1, updated.favoriteObjects.size)
        assertEquals(100, updated.pageSize)
        assertTrue(updated.strictSafetyEnabled)
        assertEquals("TRANSACTION_PER_SHARD", updated.transactionMode)
    }

    @Test
    fun `state service keeps workspace state isolated by workspace id while library and preferences stay shared`() {
        val storageDir = Files.createTempDirectory("sql-console-state-workspaces")
        val service = SqlConsoleStateService(storageDir)

        service.updateState(
            request = com.sbrf.lt.platform.ui.model.SqlConsoleStateUpdateRequest(
                draftSql = "select * from dev_table",
                recentQueries = listOf("select * from dev_table"),
                selectedGroupNames = listOf("dev"),
                selectedSourceNames = listOf("db1", "db2"),
                pageSize = 100,
            ),
            workspaceId = "workspace-a",
        )
        val emptyWorkspace = service.currentState("workspace-b")
        assertEquals("select 1 as check_value", emptyWorkspace.draftSql)
        assertEquals(null, emptyWorkspace.selectedGroupNames)
        assertEquals(emptyList(), emptyWorkspace.selectedSourceNames)
        service.updateState(
            request = com.sbrf.lt.platform.ui.model.SqlConsoleStateUpdateRequest(
                draftSql = "select * from ift_table",
                recentQueries = listOf("select * from ift_table"),
                selectedGroupNames = listOf("ift"),
                selectedSourceNames = listOf("db2", "db3"),
                pageSize = 25,
            ),
            workspaceId = "workspace-b",
        )

        val workspaceA = service.currentState("workspace-a")
        val workspaceB = service.currentState("workspace-b")

        assertEquals("select * from dev_table", workspaceA.draftSql)
        assertEquals(listOf("dev"), workspaceA.selectedGroupNames)
        assertEquals(listOf("db1", "db2"), workspaceA.selectedSourceNames)
        assertEquals("select * from ift_table", workspaceB.draftSql)
        assertEquals(listOf("ift"), workspaceB.selectedGroupNames)
        assertEquals(listOf("db2", "db3"), workspaceB.selectedSourceNames)
        assertEquals(listOf("select * from ift_table"), workspaceA.recentQueries)
        assertEquals(25, workspaceA.pageSize)
        assertEquals(25, workspaceB.pageSize)
    }
}
