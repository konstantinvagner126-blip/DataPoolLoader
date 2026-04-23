package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

class SqlConsoleApiClient(
    private val httpClient: ComposeHttpClient = ComposeHttpClient(),
) : SqlConsoleApi {
    private fun sqlConsoleStatePath(workspaceId: String? = null): String =
        "/api/sql-console/state?workspaceId=${urlEncode(workspaceId ?: resolveSqlConsoleWorkspaceId())}"

    private fun sqlConsoleHistoryPath(workspaceId: String? = null): String =
        "/api/sql-console/history?workspaceId=${urlEncode(workspaceId ?: resolveSqlConsoleWorkspaceId())}"

    override suspend fun loadRuntimeContext(): RuntimeContext =
        httpClient.get("/api/ui/runtime-context", RuntimeContext.serializer())

    override suspend fun loadInfo(): SqlConsoleInfo =
        httpClient.get("/api/sql-console/info", SqlConsoleInfo.serializer())

    override suspend fun loadState(workspaceId: String?): SqlConsoleStateSnapshot =
        httpClient.get(sqlConsoleStatePath(workspaceId), SqlConsoleStateSnapshot.serializer())

    override suspend fun loadExecutionHistory(workspaceId: String?): SqlConsoleExecutionHistoryResponse =
        httpClient.get(sqlConsoleHistoryPath(workspaceId), SqlConsoleExecutionHistoryResponse.serializer())

    override suspend fun saveState(
        request: SqlConsoleStateUpdate,
        workspaceId: String?,
    ): SqlConsoleStateSnapshot =
        httpClient.postJson(
            path = sqlConsoleStatePath(workspaceId),
            payload = request,
            serializer = SqlConsoleStateUpdate.serializer(),
            deserializer = SqlConsoleStateSnapshot.serializer(),
        )

    override suspend fun saveSettings(request: SqlConsoleSettingsUpdate): SqlConsoleInfo =
        httpClient.postJson(
            path = "/api/sql-console/settings",
            payload = request,
            serializer = SqlConsoleSettingsUpdate.serializer(),
            deserializer = SqlConsoleInfo.serializer(),
        )

    override suspend fun checkConnections(): SqlConsoleConnectionCheckResponse =
        httpClient.postJson(
            path = "/api/sql-console/connections/check",
            payload = emptyMap<String, String>(),
            serializer = MapSerializer(String.serializer(), String.serializer()),
            deserializer = SqlConsoleConnectionCheckResponse.serializer(),
        )

    override suspend fun searchObjects(request: SqlConsoleObjectSearchRequest): SqlConsoleObjectSearchResponse =
        httpClient.postJson(
            path = "/api/sql-console/objects/search",
            payload = request,
            serializer = SqlConsoleObjectSearchRequest.serializer(),
            deserializer = SqlConsoleObjectSearchResponse.serializer(),
        )

    override suspend fun inspectObject(request: SqlConsoleObjectInspectorRequest): SqlConsoleObjectInspectorResponse =
        httpClient.postJson(
            path = "/api/sql-console/objects/inspect",
            payload = request,
            serializer = SqlConsoleObjectInspectorRequest.serializer(),
            deserializer = SqlConsoleObjectInspectorResponse.serializer(),
        )

    override suspend fun loadObjectColumns(request: SqlConsoleObjectColumnsRequest): SqlConsoleObjectColumnsResponse =
        httpClient.postJson(
            path = "/api/sql-console/objects/columns",
            payload = request,
            serializer = SqlConsoleObjectColumnsRequest.serializer(),
            deserializer = SqlConsoleObjectColumnsResponse.serializer(),
        )

    override suspend fun startQuery(request: SqlConsoleQueryStartRequest): SqlConsoleStartQueryResponse =
        httpClient.postJson(
            path = "/api/sql-console/query/start",
            payload = request,
            serializer = SqlConsoleQueryStartRequest.serializer(),
            deserializer = SqlConsoleStartQueryResponse.serializer(),
        )

    override suspend fun loadExecution(executionId: String): SqlConsoleExecutionResponse =
        httpClient.get("/api/sql-console/query/$executionId", SqlConsoleExecutionResponse.serializer())

    override suspend fun heartbeatExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse =
        httpClient.postJson(
            path = "/api/sql-console/query/$executionId/heartbeat",
            payload = request,
            serializer = SqlConsoleExecutionOwnerActionRequest.serializer(),
            deserializer = SqlConsoleExecutionResponse.serializer(),
        )

    override suspend fun releaseExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse =
        httpClient.postJson(
            path = "/api/sql-console/query/$executionId/release",
            payload = request,
            serializer = SqlConsoleExecutionOwnerActionRequest.serializer(),
            deserializer = SqlConsoleExecutionResponse.serializer(),
        )

    override suspend fun cancelExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse =
        httpClient.postJson(
            path = "/api/sql-console/query/$executionId/cancel",
            payload = request,
            serializer = SqlConsoleExecutionOwnerActionRequest.serializer(),
            deserializer = SqlConsoleExecutionResponse.serializer(),
        )

    override suspend fun commitExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse =
        httpClient.postJson(
            path = "/api/sql-console/query/$executionId/commit",
            payload = request,
            serializer = SqlConsoleExecutionOwnerActionRequest.serializer(),
            deserializer = SqlConsoleExecutionResponse.serializer(),
        )

    override suspend fun rollbackExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse =
        httpClient.postJson(
            path = "/api/sql-console/query/$executionId/rollback",
            payload = request,
            serializer = SqlConsoleExecutionOwnerActionRequest.serializer(),
            deserializer = SqlConsoleExecutionResponse.serializer(),
        )
}
