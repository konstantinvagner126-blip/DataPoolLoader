package com.sbrf.lt.platform.composeui.sql_console

interface SqlConsoleApi {
    suspend fun loadInfo(): SqlConsoleInfo

    suspend fun loadState(): SqlConsoleStateSnapshot

    suspend fun saveState(request: SqlConsoleStateUpdate): SqlConsoleStateSnapshot

    suspend fun saveSettings(request: SqlConsoleSettingsUpdate): SqlConsoleInfo

    suspend fun checkConnections(): SqlConsoleConnectionCheckResponse

    suspend fun startQuery(request: SqlConsoleQueryStartRequest): SqlConsoleStartQueryResponse

    suspend fun loadExecution(executionId: String): SqlConsoleExecutionResponse

    suspend fun cancelExecution(executionId: String): SqlConsoleExecutionResponse
}

