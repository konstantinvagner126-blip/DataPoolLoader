package com.sbrf.lt.platform.composeui.sql_console

class SqlConsoleObjectsStore(
    private val api: SqlConsoleApi,
) {
    private val loadingSupport = SqlConsoleObjectsStoreLoadingSupport(api)
    private val actionSupport = SqlConsoleObjectsStoreActionSupport(api)

    suspend fun load(workspaceId: String? = null): SqlConsoleObjectsPageState =
        loadingSupport.load(workspaceId)

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
        val selectionUpdate = toggleSelectedSourceWithGroups(
            groups = current.info?.groups.orEmpty(),
            currentSelectedGroupNames = current.selectedGroupNames,
            currentSelectedSourceNames = current.selectedSourceNames,
            manuallyIncludedSourceNames = current.manuallyIncludedSourceNames,
            manuallyExcludedSourceNames = current.manuallyExcludedSourceNames,
            sourceName = sourceName,
            enabled = enabled,
        )
        return current.copy(
            selectedSourceNames = selectionUpdate.selectedSourceNames,
            selectedGroupNames = selectionUpdate.selectedGroupNames,
            manuallyIncludedSourceNames = selectionUpdate.manuallyIncludedSourceNames,
            manuallyExcludedSourceNames = selectionUpdate.manuallyExcludedSourceNames,
        )
    }

    fun updateSelectedSourceGroup(
        current: SqlConsoleObjectsPageState,
        group: SqlConsoleSourceGroup,
        enabled: Boolean,
    ): SqlConsoleObjectsPageState {
        val selectionUpdate = toggleSelectedSourceGroupNames(
            groups = current.info?.groups.orEmpty(),
            currentSelectedGroupNames = current.selectedGroupNames,
            currentSelectedSourceNames = current.selectedSourceNames,
            manuallyIncludedSourceNames = current.manuallyIncludedSourceNames,
            manuallyExcludedSourceNames = current.manuallyExcludedSourceNames,
            group = group,
            enabled = enabled,
        )
        return current.copy(
            selectedSourceNames = selectionUpdate.selectedSourceNames,
            selectedGroupNames = selectionUpdate.selectedGroupNames,
            manuallyIncludedSourceNames = selectionUpdate.manuallyIncludedSourceNames,
            manuallyExcludedSourceNames = selectionUpdate.manuallyExcludedSourceNames,
        )
    }

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
