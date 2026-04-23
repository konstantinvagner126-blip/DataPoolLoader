package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleObjectsStoreActionSupport(
    private val api: SqlConsoleApi,
) {
    private val persistenceSupport = SqlConsoleObjectsStorePersistenceSupport(api)
    private val favoriteSupport = SqlConsoleObjectsStoreFavoriteSupport(persistenceSupport)
    private val searchSupport = SqlConsoleObjectsStoreSearchSupport(api)
    private val inspectorSupport = SqlConsoleObjectsStoreInspectorSupport(api)

    suspend fun persistState(
        current: SqlConsoleObjectsPageState,
        workspaceId: String,
    ): SqlConsoleObjectsPageState =
        persistenceSupport.persistState(current, workspaceId)

    suspend fun toggleFavoriteObject(
        current: SqlConsoleObjectsPageState,
        workspaceId: String,
        sourceName: String,
        value: SqlConsoleDatabaseObject,
    ): SqlConsoleObjectsPageState =
        favoriteSupport.toggleFavoriteObject(current, workspaceId, sourceName, value)

    suspend fun openObjectInConsole(
        current: SqlConsoleObjectsPageState,
        workspaceId: String,
        sourceName: String,
        draftSql: String,
    ): SqlConsoleObjectsPageState =
        persistenceSupport.openObjectInConsole(current, workspaceId, sourceName, draftSql)

    suspend fun search(current: SqlConsoleObjectsPageState): SqlConsoleObjectsPageState =
        searchSupport.search(current)

    suspend fun loadInspector(
        current: SqlConsoleObjectsPageState,
        sourceName: String,
        value: SqlConsoleDatabaseObject,
    ): SqlConsoleObjectsPageState =
        inspectorSupport.loadInspector(current, sourceName, value)
}
