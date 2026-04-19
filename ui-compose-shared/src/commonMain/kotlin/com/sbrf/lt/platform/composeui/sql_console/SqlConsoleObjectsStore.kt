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
    ): SqlConsoleObjectsPageState {
        val next = if (enabled) {
            (current.selectedSourceNames + sourceName).distinct()
        } else {
            current.selectedSourceNames.filterNot { it == sourceName }
        }
        return current.copy(selectedSourceNames = next)
    }

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
}

internal fun SqlConsoleDatabaseObject.toFavoriteObject(sourceName: String): SqlConsoleFavoriteObject =
    SqlConsoleFavoriteObject(
        sourceName = sourceName,
        schemaName = schemaName,
        objectName = objectName,
        objectType = objectType,
        tableName = tableName,
    )
