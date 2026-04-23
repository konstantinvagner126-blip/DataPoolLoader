package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleStoreExecutionSupport(
    private val api: SqlConsoleApi,
) {
    private val settingsSupport = SqlConsoleStoreSettingsSupport(api)
    private val queryLifecycleSupport = SqlConsoleStoreQueryLifecycleSupport(api)
    private val ownerActionSupport = SqlConsoleStoreOwnerActionSupport(api)

    suspend fun saveSettings(current: SqlConsolePageState): SqlConsolePageState =
        settingsSupport.saveSettings(current)

    suspend fun checkConnections(current: SqlConsolePageState): SqlConsolePageState =
        settingsSupport.checkConnections(current)

    suspend fun startQuery(
        current: SqlConsolePageState,
        workspaceId: String,
        ownerSessionId: String,
        sqlOverride: String? = null,
        successMessage: String = "Запрос запущен.",
    ): SqlConsolePageState =
        queryLifecycleSupport.startQuery(current, workspaceId, ownerSessionId, sqlOverride, successMessage)

    suspend fun refreshExecution(current: SqlConsolePageState): SqlConsolePageState =
        queryLifecycleSupport.refreshExecution(current)

    suspend fun restoreExecution(
        current: SqlConsolePageState,
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsolePageState =
        queryLifecycleSupport.restoreExecution(current, executionId, ownerSessionId, ownerToken)

    suspend fun heartbeatExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState =
        ownerActionSupport.heartbeatExecution(current, ownerSessionId)

    suspend fun cancelExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState =
        ownerActionSupport.cancelExecution(current, ownerSessionId)

    suspend fun commitExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState =
        ownerActionSupport.commitExecution(current, ownerSessionId)

    suspend fun rollbackExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState =
        ownerActionSupport.rollbackExecution(current, ownerSessionId)
}
