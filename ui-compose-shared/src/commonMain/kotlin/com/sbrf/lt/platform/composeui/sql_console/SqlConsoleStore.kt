package com.sbrf.lt.platform.composeui.sql_console

class SqlConsoleStore(
    private val api: SqlConsoleApi,
) {
    private val loadingSupport = SqlConsoleStoreLoadingSupport(api)
    private val executionSupport = SqlConsoleStoreExecutionSupport(api)

    suspend fun load(): SqlConsolePageState =
        loadingSupport.load()

    fun startLoading(current: SqlConsolePageState): SqlConsolePageState =
        current.copy(loading = true, errorMessage = null, successMessage = null)

    fun updateDraftSql(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        current.copy(draftSql = value)

    fun updateSelectedSources(
        current: SqlConsolePageState,
        sourceName: String,
        enabled: Boolean,
    ): SqlConsolePageState =
        current.copy(
            selectedSourceNames = toggleSelectedSourceNames(
                current.selectedSourceNames,
                sourceName,
                enabled,
            ),
        )

    fun updateSelectedSourceGroup(
        current: SqlConsolePageState,
        group: SqlConsoleSourceGroup,
        enabled: Boolean,
    ): SqlConsolePageState =
        current.copy(
            selectedSourceNames = toggleSelectedSourceGroupNames(
                current = current.selectedSourceNames,
                group = group,
                enabled = enabled,
            ),
        )

    fun updatePageSize(
        current: SqlConsolePageState,
        pageSize: Int,
    ): SqlConsolePageState =
        current.copy(pageSize = normalizePageSize(pageSize))

    fun updateStrictSafety(
        current: SqlConsolePageState,
        enabled: Boolean,
    ): SqlConsolePageState =
        current.copy(strictSafetyEnabled = enabled)

    fun updateAutoCommitEnabled(
        current: SqlConsolePageState,
        enabled: Boolean,
    ): SqlConsolePageState =
        current.copy(
            transactionMode = if (enabled) "AUTO_COMMIT" else "TRANSACTION_PER_SHARD",
        )

    fun updateMaxRowsPerShardDraft(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        current.copy(maxRowsPerShardDraft = value)

    fun updateQueryTimeoutDraft(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        current.copy(queryTimeoutSecDraft = value)

    fun applyRecentQuery(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        if (value.isBlank()) current else current.copy(draftSql = value)

    fun applyFavoriteQuery(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        if (value.isBlank()) current else current.copy(draftSql = value)

    fun rememberFavoriteQuery(current: SqlConsolePageState): SqlConsolePageState {
        val sql = current.draftSql.trim()
        if (sql.isBlank()) {
            return current.copy(errorMessage = "Сначала введи SQL-запрос.", successMessage = null)
        }
        return current.copy(
            errorMessage = null,
            successMessage = "Запрос добавлен в избранное.",
            favoriteQueries = rememberQuery(current.favoriteQueries, sql, limit = 20),
        )
    }

    fun removeFavoriteQuery(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        current.copy(
            favoriteQueries = current.favoriteQueries.filterNot { it == value },
            errorMessage = null,
            successMessage = "Запрос убран из избранного.",
        )

    fun removeFavoriteObject(
        current: SqlConsolePageState,
        value: SqlConsoleFavoriteObject,
    ): SqlConsolePageState =
        current.copy(
            favoriteObjects = current.favoriteObjects.filterNot { it.matches(value) },
            errorMessage = null,
            successMessage = "Объект убран из избранного.",
        )

    fun clearRecentQueries(current: SqlConsolePageState): SqlConsolePageState =
        current.copy(
            recentQueries = emptyList(),
            errorMessage = null,
            successMessage = "История последних запросов очищена.",
        )

    suspend fun persistState(current: SqlConsolePageState): SqlConsolePageState =
        loadingSupport.persistState(current)

    suspend fun saveSettings(current: SqlConsolePageState): SqlConsolePageState =
        executionSupport.saveSettings(current)

    suspend fun checkConnections(current: SqlConsolePageState): SqlConsolePageState =
        executionSupport.checkConnections(current)

    suspend fun startQuery(
        current: SqlConsolePageState,
        ownerSessionId: String,
        sqlOverride: String? = null,
        successMessage: String = "Запрос запущен.",
    ): SqlConsolePageState =
        executionSupport.startQuery(current, ownerSessionId, sqlOverride, successMessage)

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
        current.copy(actionInProgress = actionName, errorMessage = null, successMessage = null)
}
