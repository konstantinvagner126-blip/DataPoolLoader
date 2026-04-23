package com.sbrf.lt.platform.composeui.sql_console

class SqlConsoleObjectsStore(
    private val api: SqlConsoleApi,
) {
    private val loadingSupport = SqlConsoleObjectsStoreLoadingSupport(api)
    private val actionSupport = SqlConsoleObjectsStoreActionSupport(api)

    suspend fun load(): SqlConsoleObjectsPageState =
        loadingSupport.load()

    fun startLoading(current: SqlConsoleObjectsPageState): SqlConsoleObjectsPageState =
        current.copy(loading = true, errorMessage = null, successMessage = null)

    fun beginAction(
        current: SqlConsoleObjectsPageState,
        actionName: String,
    ): SqlConsoleObjectsPageState =
        current.copy(actionInProgress = actionName, errorMessage = null, successMessage = null)

    fun updateQuery(
        current: SqlConsoleObjectsPageState,
        value: String,
    ): SqlConsoleObjectsPageState =
        current.copy(query = value)

    fun updateSelectedSources(
        current: SqlConsoleObjectsPageState,
        sourceName: String,
        enabled: Boolean,
    ): SqlConsoleObjectsPageState =
        current.copy(
            selectedSourceNames = toggleSelectedSourceNames(
                current.selectedSourceNames,
                sourceName,
                enabled,
            ),
        )

    suspend fun toggleFavoriteObject(
        current: SqlConsoleObjectsPageState,
        sourceName: String,
        value: SqlConsoleDatabaseObject,
    ): SqlConsoleObjectsPageState =
        actionSupport.toggleFavoriteObject(current, sourceName, value)

    suspend fun openObjectInConsole(
        current: SqlConsoleObjectsPageState,
        sourceName: String,
        draftSql: String,
    ): SqlConsoleObjectsPageState =
        actionSupport.openObjectInConsole(current, sourceName, draftSql)

    suspend fun search(current: SqlConsoleObjectsPageState): SqlConsoleObjectsPageState =
        actionSupport.search(current)

    fun beginInspectorLoad(current: SqlConsoleObjectsPageState): SqlConsoleObjectsPageState =
        current.copy(
            inspectorLoading = true,
            inspectorErrorMessage = null,
            inspectorResponse = null,
        )

    fun clearInspector(current: SqlConsoleObjectsPageState): SqlConsoleObjectsPageState =
        current.copy(
            inspectorLoading = false,
            inspectorErrorMessage = null,
            inspectorResponse = null,
        )

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
