package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.model.RuntimeContext

interface SqlConsoleApi {
    suspend fun loadRuntimeContext(): RuntimeContext

    suspend fun loadInfo(): SqlConsoleInfo

    suspend fun loadState(workspaceId: String? = null): SqlConsoleStateSnapshot

    suspend fun loadExecutionHistory(workspaceId: String? = null): SqlConsoleExecutionHistoryResponse

    suspend fun saveState(
        request: SqlConsoleStateUpdate,
        workspaceId: String? = null,
    ): SqlConsoleStateSnapshot

    suspend fun saveSettings(request: SqlConsoleSettingsUpdate): SqlConsoleInfo

    suspend fun loadSourceSettings(): SqlConsoleSourceSettings

    suspend fun saveSourceSettings(request: SqlConsoleSourceSettingsUpdate): SqlConsoleSourceSettings

    suspend fun testSourceSettingsConnection(
        request: SqlConsoleSourceSettingsConnectionTestRequest,
    ): SqlConsoleSourceSettingsConnectionTestResponse

    suspend fun testSourceSettingsConnections(
        request: SqlConsoleSourceSettingsConnectionsTestRequest,
    ): SqlConsoleSourceSettingsConnectionsTestResponse

    suspend fun diagnoseSourceSettingsCredentials(
        request: SqlConsoleSourceSettingsCredentialsDiagnosticsRequest,
    ): SqlConsoleSourceSettingsCredentialsDiagnosticsResponse

    suspend fun pickSourceSettingsCredentialsFile(
        request: SqlConsoleSourceSettingsFilePickRequest,
    ): SqlConsoleSourceSettingsFilePickResponse

    suspend fun checkConnections(): SqlConsoleConnectionCheckResponse

    suspend fun searchObjects(request: SqlConsoleObjectSearchRequest): SqlConsoleObjectSearchResponse

    suspend fun inspectObject(request: SqlConsoleObjectInspectorRequest): SqlConsoleObjectInspectorResponse

    suspend fun loadObjectColumns(request: SqlConsoleObjectColumnsRequest): SqlConsoleObjectColumnsResponse

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
