package com.sbrf.lt.platform.composeui.sql_console

class SqlConsoleStore(
    private val api: SqlConsoleApi,
) {
    private val loadingSupport = SqlConsoleStoreLoadingSupport(api)
    private val executionSupport = SqlConsoleStoreExecutionSupport(api)
    private val stateSupport = SqlConsoleStoreStateSupport()
    private val librarySupport = SqlConsoleStoreLibrarySupport()

    suspend fun load(workspaceId: String? = null): SqlConsolePageState =
        loadingSupport.load(workspaceId)

    fun startLoading(current: SqlConsolePageState): SqlConsolePageState =
        stateSupport.startLoading(current)

    fun updateDraftSql(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        stateSupport.updateDraftSql(current, value)

    fun updateSelectedSources(
        current: SqlConsolePageState,
        sourceName: String,
        enabled: Boolean,
    ): SqlConsolePageState =
        stateSupport.updateSelectedSources(current, sourceName, enabled)

    fun updateSelectedSourceGroup(
        current: SqlConsolePageState,
        group: SqlConsoleSourceGroup,
        enabled: Boolean,
    ): SqlConsolePageState =
        stateSupport.updateSelectedSourceGroup(current, group, enabled)

    fun updatePageSize(
        current: SqlConsolePageState,
        pageSize: Int,
    ): SqlConsolePageState =
        stateSupport.updatePageSize(current, pageSize)

    fun updateStrictSafety(
        current: SqlConsolePageState,
        enabled: Boolean,
    ): SqlConsolePageState =
        stateSupport.updateStrictSafety(current, enabled)

    fun updateAutoCommitEnabled(
        current: SqlConsolePageState,
        enabled: Boolean,
    ): SqlConsolePageState =
        stateSupport.updateAutoCommitEnabled(current, enabled)

    fun updateMaxRowsPerShardDraft(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        stateSupport.updateMaxRowsPerShardDraft(current, value)

    fun updateQueryTimeoutDraft(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        stateSupport.updateQueryTimeoutDraft(current, value)

    fun applyRecentQuery(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        librarySupport.applyRecentQuery(current, value)

    fun applyFavoriteQuery(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        librarySupport.applyFavoriteQuery(current, value)

    fun rememberFavoriteQuery(current: SqlConsolePageState): SqlConsolePageState =
        librarySupport.rememberFavoriteQuery(current)

    fun removeFavoriteQuery(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        librarySupport.removeFavoriteQuery(current, value)

    fun removeFavoriteObject(
        current: SqlConsolePageState,
        value: SqlConsoleFavoriteObject,
    ): SqlConsolePageState =
        librarySupport.removeFavoriteObject(current, value)

    fun clearRecentQueries(current: SqlConsolePageState): SqlConsolePageState =
        librarySupport.clearRecentQueries(current)

    suspend fun persistState(current: SqlConsolePageState): SqlConsolePageState =
        loadingSupport.persistState(current)

    suspend fun persistState(
        current: SqlConsolePageState,
        workspaceId: String,
    ): SqlConsolePageState =
        loadingSupport.persistState(current, workspaceId)

    suspend fun saveSettings(current: SqlConsolePageState): SqlConsolePageState =
        executionSupport.saveSettings(current)

    suspend fun checkConnections(current: SqlConsolePageState): SqlConsolePageState =
        executionSupport.checkConnections(current)

    suspend fun startQuery(
        current: SqlConsolePageState,
        workspaceId: String,
        ownerSessionId: String,
        sqlOverride: String? = null,
        successMessage: String = "Запрос запущен.",
    ): SqlConsolePageState =
        executionSupport.startQuery(current, workspaceId, ownerSessionId, sqlOverride, successMessage)

    suspend fun refreshExecution(current: SqlConsolePageState): SqlConsolePageState =
        executionSupport.refreshExecution(current)

    suspend fun restoreExecution(
        current: SqlConsolePageState,
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsolePageState =
        executionSupport.restoreExecution(current, executionId, ownerSessionId, ownerToken)

    suspend fun heartbeatExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState =
        executionSupport.heartbeatExecution(current, ownerSessionId)

    suspend fun cancelExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState =
        executionSupport.cancelExecution(current, ownerSessionId)

    suspend fun commitExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState =
        executionSupport.commitExecution(current, ownerSessionId)

    suspend fun rollbackExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState =
        executionSupport.rollbackExecution(current, ownerSessionId)

    fun beginAction(
        current: SqlConsolePageState,
        actionName: String,
    ): SqlConsolePageState =
        stateSupport.beginAction(current, actionName)
}
