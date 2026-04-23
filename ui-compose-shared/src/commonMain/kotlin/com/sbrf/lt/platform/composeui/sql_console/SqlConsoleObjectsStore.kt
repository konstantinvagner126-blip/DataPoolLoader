package com.sbrf.lt.platform.composeui.sql_console

class SqlConsoleObjectsStore(
    private val api: SqlConsoleApi,
) {
    private val loadingSupport = SqlConsoleObjectsStoreLoadingSupport(api)
    private val actionSupport = SqlConsoleObjectsStoreActionSupport(api)
    private val stateSupport = SqlConsoleObjectsStoreStateSupport()

    suspend fun load(workspaceId: String? = null): SqlConsoleObjectsPageState =
        loadingSupport.load(workspaceId)

    fun startLoading(current: SqlConsoleObjectsPageState): SqlConsoleObjectsPageState =
        stateSupport.startLoading(current)

    fun beginAction(
        current: SqlConsoleObjectsPageState,
        actionName: String,
    ): SqlConsoleObjectsPageState =
        stateSupport.beginAction(current, actionName)

    fun updateQuery(
        current: SqlConsoleObjectsPageState,
        value: String,
    ): SqlConsoleObjectsPageState =
        stateSupport.updateQuery(current, value)

    fun updateSelectedSources(
        current: SqlConsoleObjectsPageState,
        sourceName: String,
        enabled: Boolean,
    ): SqlConsoleObjectsPageState =
        stateSupport.updateSelectedSources(current, sourceName, enabled)

    fun updateSelectedSourceGroup(
        current: SqlConsoleObjectsPageState,
        group: SqlConsoleSourceGroup,
        enabled: Boolean,
    ): SqlConsoleObjectsPageState =
        stateSupport.updateSelectedSourceGroup(current, group, enabled)

    suspend fun toggleFavoriteObject(
        current: SqlConsoleObjectsPageState,
        workspaceId: String,
        sourceName: String,
        value: SqlConsoleDatabaseObject,
    ): SqlConsoleObjectsPageState =
        actionSupport.toggleFavoriteObject(current, workspaceId, sourceName, value)

    suspend fun persistState(
        current: SqlConsoleObjectsPageState,
        workspaceId: String,
    ): SqlConsoleObjectsPageState =
        actionSupport.persistState(current, workspaceId)

    suspend fun openObjectInConsole(
        current: SqlConsoleObjectsPageState,
        workspaceId: String,
        sourceName: String,
        draftSql: String,
    ): SqlConsoleObjectsPageState =
        actionSupport.openObjectInConsole(current, workspaceId, sourceName, draftSql)

    suspend fun search(current: SqlConsoleObjectsPageState): SqlConsoleObjectsPageState =
        actionSupport.search(current)

    fun beginInspectorLoad(current: SqlConsoleObjectsPageState): SqlConsoleObjectsPageState =
        stateSupport.beginInspectorLoad(current)

    fun clearInspector(current: SqlConsoleObjectsPageState): SqlConsoleObjectsPageState =
        stateSupport.clearInspector(current)

    suspend fun loadInspector(
        current: SqlConsoleObjectsPageState,
        sourceName: String,
        value: SqlConsoleDatabaseObject,
    ): SqlConsoleObjectsPageState =
        actionSupport.loadInspector(current, sourceName, value)
}

internal fun SqlConsoleDatabaseObject.toFavoriteObject(sourceName: String): SqlConsoleFavoriteObject =
    SqlConsoleFavoriteObject(
        sourceName = sourceName,
        schemaName = schemaName,
        objectName = objectName,
        objectType = objectType,
        tableName = tableName,
    )
