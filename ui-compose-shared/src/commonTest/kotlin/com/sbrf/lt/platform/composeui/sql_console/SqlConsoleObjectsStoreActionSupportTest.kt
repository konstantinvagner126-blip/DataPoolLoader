package com.sbrf.lt.platform.composeui.sql_console

import kotlin.test.Test
import kotlin.test.assertEquals

class SqlConsoleObjectsStoreActionSupportTest {

    @Test
    fun `toggle favorite object persists updated favorites in workspace state`() {
        var savedRequest: SqlConsoleStateUpdate? = null
        val store = SqlConsoleObjectsStore(
            StubSqlConsoleApi(
                saveStateHandler = { request, _ ->
                    savedRequest = request
                    SqlConsoleStateSnapshot(
                        draftSql = request.draftSql,
                        recentQueries = request.recentQueries,
                        favoriteQueries = request.favoriteQueries,
                        favoriteObjects = request.favoriteObjects,
                        selectedGroupNames = request.selectedGroupNames,
                        selectedSourceNames = request.selectedSourceNames,
                        pageSize = request.pageSize,
                        strictSafetyEnabled = request.strictSafetyEnabled,
                        transactionMode = request.transactionMode,
                    )
                },
            ),
        )

        val dbObject = SqlConsoleDatabaseObject(
            schemaName = "public",
            objectName = "offer",
            objectType = "TABLE",
        )
        val current = SqlConsoleObjectsPageState(
            persistedState = defaultSqlConsoleStateSnapshot(),
            selectedGroupNames = listOf("dev"),
            selectedSourceNames = listOf("db1", "db2"),
        )

        val next = runSuspend {
            store.toggleFavoriteObject(
                current = current,
                workspaceId = "workspace-a",
                sourceName = "db1",
                value = dbObject,
            )
        }

        assertEquals(listOf("dev"), savedRequest?.selectedGroupNames)
        assertEquals(listOf("db1", "db2"), savedRequest?.selectedSourceNames)
        assertEquals(1, savedRequest?.favoriteObjects?.size)
        assertEquals("Объект добавлен в избранное.", next.successMessage)
        assertEquals(1, next.favoriteObjects.size)
        assertEquals("offer", next.favoriteObjects.single().objectName)
    }

    @Test
    fun `open object in console rewrites workspace selection to chosen source`() {
        var savedRequest: SqlConsoleStateUpdate? = null
        var savedWorkspaceId: String? = null
        val store = SqlConsoleObjectsStore(
            StubSqlConsoleApi(
                saveStateHandler = { request, workspaceId ->
                    savedRequest = request
                    savedWorkspaceId = workspaceId
                    SqlConsoleStateSnapshot(
                        draftSql = request.draftSql,
                        recentQueries = request.recentQueries,
                        favoriteQueries = request.favoriteQueries,
                        favoriteObjects = request.favoriteObjects,
                        selectedGroupNames = request.selectedGroupNames,
                        selectedSourceNames = request.selectedSourceNames,
                        pageSize = request.pageSize,
                        strictSafetyEnabled = request.strictSafetyEnabled,
                        transactionMode = request.transactionMode,
                    )
                },
            ),
        )

        val favoriteObject = SqlConsoleFavoriteObject(
            sourceName = "db1",
            schemaName = "public",
            objectName = "offer",
            objectType = "TABLE",
        )
        val current = SqlConsoleObjectsPageState(
            info = sampleSqlConsoleInfo(),
            persistedState = SqlConsoleStateSnapshot(
                draftSql = "select 1",
                selectedGroupNames = listOf("dev"),
                selectedSourceNames = listOf("db1", "db2"),
                favoriteObjects = listOf(favoriteObject),
            ),
            selectedGroupNames = listOf("dev"),
            selectedSourceNames = listOf("db1", "db2"),
            favoriteObjects = listOf(favoriteObject),
        )

        val next = runSuspend {
            store.openObjectInConsole(
                current = current,
                workspaceId = "workspace-a",
                sourceName = "db3",
                draftSql = "select * from public.offer limit 100;",
            )
        }

        assertEquals("workspace-a", savedWorkspaceId)
        assertEquals(emptyList(), savedRequest?.selectedGroupNames)
        assertEquals(listOf("db3"), savedRequest?.selectedSourceNames)
        assertEquals("select * from public.offer limit 100;", savedRequest?.draftSql)
        assertEquals(listOf(favoriteObject), savedRequest?.favoriteObjects)
        assertEquals("SQL-шаблон подготовлен.", next.successMessage)
        assertEquals(listOf("db3"), next.persistedState?.selectedSourceNames)
        assertEquals(emptyList(), next.persistedState?.selectedGroupNames)
    }
}
