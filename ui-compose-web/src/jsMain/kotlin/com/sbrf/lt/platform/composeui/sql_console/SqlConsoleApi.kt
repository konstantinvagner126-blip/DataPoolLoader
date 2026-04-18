package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

class SqlConsoleApiClient(
    private val httpClient: ComposeHttpClient = ComposeHttpClient(),
) : SqlConsoleApi {
    override suspend fun loadRuntimeContext(): RuntimeContext =
        httpClient.get("/api/ui/runtime-context", RuntimeContext.serializer())

    override suspend fun loadInfo(): SqlConsoleInfo =
        httpClient.get("/api/sql-console/info", SqlConsoleInfo.serializer())

    override suspend fun loadState(): SqlConsoleStateSnapshot =
        httpClient.get("/api/sql-console/state", SqlConsoleStateSnapshot.serializer())

    override suspend fun saveState(request: SqlConsoleStateUpdate): SqlConsoleStateSnapshot =
        httpClient.postJson(
            path = "/api/sql-console/state",
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

    override suspend fun startQuery(request: SqlConsoleQueryStartRequest): SqlConsoleStartQueryResponse =
        httpClient.postJson(
            path = "/api/sql-console/query/start",
            payload = request,
            serializer = SqlConsoleQueryStartRequest.serializer(),
            deserializer = SqlConsoleStartQueryResponse.serializer(),
        )

    override suspend fun loadExecution(executionId: String): SqlConsoleExecutionResponse =
        httpClient.get("/api/sql-console/query/$executionId", SqlConsoleExecutionResponse.serializer())

    override suspend fun cancelExecution(executionId: String): SqlConsoleExecutionResponse =
        httpClient.postJson(
            path = "/api/sql-console/query/$executionId/cancel",
            payload = emptyMap<String, String>(),
            serializer = MapSerializer(String.serializer(), String.serializer()),
            deserializer = SqlConsoleExecutionResponse.serializer(),
        )
}
