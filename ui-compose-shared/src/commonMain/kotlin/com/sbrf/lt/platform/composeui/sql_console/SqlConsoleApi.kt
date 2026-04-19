package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.model.RuntimeContext

interface SqlConsoleApi {
    suspend fun loadRuntimeContext(): RuntimeContext

    suspend fun loadInfo(): SqlConsoleInfo

    suspend fun loadState(): SqlConsoleStateSnapshot

    suspend fun saveState(request: SqlConsoleStateUpdate): SqlConsoleStateSnapshot

    suspend fun saveSettings(request: SqlConsoleSettingsUpdate): SqlConsoleInfo

    suspend fun checkConnections(): SqlConsoleConnectionCheckResponse

    suspend fun searchObjects(request: SqlConsoleObjectSearchRequest): SqlConsoleObjectSearchResponse

    suspend fun startQuery(request: SqlConsoleQueryStartRequest): SqlConsoleStartQueryResponse

    suspend fun loadExecution(executionId: String): SqlConsoleExecutionResponse

    suspend fun cancelExecution(executionId: String): SqlConsoleExecutionResponse

    suspend fun commitExecution(executionId: String): SqlConsoleExecutionResponse

    suspend fun rollbackExecution(executionId: String): SqlConsoleExecutionResponse
}
