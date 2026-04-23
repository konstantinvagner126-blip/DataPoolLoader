package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.model.RuntimeContext

interface SqlConsoleApi {
    suspend fun loadRuntimeContext(): RuntimeContext

    suspend fun loadInfo(): SqlConsoleInfo

    suspend fun loadState(workspaceId: String? = null): SqlConsoleStateSnapshot

    suspend fun saveState(
        request: SqlConsoleStateUpdate,
        workspaceId: String? = null,
    ): SqlConsoleStateSnapshot

    suspend fun saveSettings(request: SqlConsoleSettingsUpdate): SqlConsoleInfo

    suspend fun checkConnections(): SqlConsoleConnectionCheckResponse

    suspend fun searchObjects(request: SqlConsoleObjectSearchRequest): SqlConsoleObjectSearchResponse

    suspend fun inspectObject(request: SqlConsoleObjectInspectorRequest): SqlConsoleObjectInspectorResponse

    suspend fun startQuery(request: SqlConsoleQueryStartRequest): SqlConsoleStartQueryResponse

    suspend fun loadExecution(executionId: String): SqlConsoleExecutionResponse

    suspend fun heartbeatExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse

    suspend fun releaseExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse

    suspend fun cancelExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse

    suspend fun commitExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse

    suspend fun rollbackExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse
}
