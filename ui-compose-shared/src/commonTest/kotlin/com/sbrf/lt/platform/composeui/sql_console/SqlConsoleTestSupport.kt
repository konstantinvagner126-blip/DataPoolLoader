package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.model.DatabaseConnectionStatus
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeActorState
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

internal fun sampleRuntimeContext(): RuntimeContext =
    RuntimeContext(
        requestedMode = ModuleStoreMode.FILES,
        effectiveMode = ModuleStoreMode.FILES,
        actor = RuntimeActorState(
            resolved = true,
            message = "ok",
        ),
        database = DatabaseConnectionStatus(
            configured = true,
            available = true,
            schema = "public",
            message = "ok",
        ),
    )

internal fun sampleSqlConsoleInfo(): SqlConsoleInfo =
    SqlConsoleInfo(
        configured = true,
        sourceCatalog = listOf(
            SqlConsoleSourceCatalogEntry(name = "db1"),
            SqlConsoleSourceCatalogEntry(name = "db2"),
            SqlConsoleSourceCatalogEntry(name = "db3"),
        ),
        groups = listOf(
            SqlConsoleSourceGroup(name = "dev", sources = listOf("db1", "db2")),
            SqlConsoleSourceGroup(name = "Без группы", sources = listOf("db3"), synthetic = true),
        ),
        maxRowsPerShard = 200,
        queryTimeoutSec = 30,
    )

internal class StubSqlConsoleApi(
    private val runtimeContextHandler: suspend () -> RuntimeContext = { sampleRuntimeContext() },
    private val infoHandler: suspend () -> SqlConsoleInfo = { sampleSqlConsoleInfo() },
    private val loadStateHandler: suspend (String?) -> SqlConsoleStateSnapshot = { defaultSqlConsoleStateSnapshot() },
    private val saveStateHandler: suspend (SqlConsoleStateUpdate, String?) -> SqlConsoleStateSnapshot = { _, _ ->
        error("saveState not configured")
    },
    private val saveSettingsHandler: suspend (SqlConsoleSettingsUpdate) -> SqlConsoleInfo = {
        error("saveSettings not configured")
    },
    private val checkConnectionsHandler: suspend () -> SqlConsoleConnectionCheckResponse = {
        error("checkConnections not configured")
    },
    private val searchObjectsHandler: suspend (SqlConsoleObjectSearchRequest) -> SqlConsoleObjectSearchResponse = {
        error("searchObjects not configured")
    },
    private val inspectObjectHandler: suspend (SqlConsoleObjectInspectorRequest) -> SqlConsoleObjectInspectorResponse = {
        error("inspectObject not configured")
    },
    private val loadObjectColumnsHandler: suspend (SqlConsoleObjectColumnsRequest) -> SqlConsoleObjectColumnsResponse = {
        error("loadObjectColumns not configured")
    },
    private val startQueryHandler: suspend (SqlConsoleQueryStartRequest) -> SqlConsoleStartQueryResponse = {
        error("startQuery not configured")
    },
    private val loadExecutionHandler: suspend (String) -> SqlConsoleExecutionResponse = {
        error("loadExecution not configured")
    },
    private val heartbeatExecutionHandler: suspend (String, SqlConsoleExecutionOwnerActionRequest) -> SqlConsoleExecutionResponse = { _, _ ->
        error("heartbeatExecution not configured")
    },
    private val releaseExecutionHandler: suspend (String, SqlConsoleExecutionOwnerActionRequest) -> SqlConsoleExecutionResponse = { _, _ ->
        error("releaseExecution not configured")
    },
    private val cancelExecutionHandler: suspend (String, SqlConsoleExecutionOwnerActionRequest) -> SqlConsoleExecutionResponse = { _, _ ->
        error("cancelExecution not configured")
    },
    private val commitExecutionHandler: suspend (String, SqlConsoleExecutionOwnerActionRequest) -> SqlConsoleExecutionResponse = { _, _ ->
        error("commitExecution not configured")
    },
    private val rollbackExecutionHandler: suspend (String, SqlConsoleExecutionOwnerActionRequest) -> SqlConsoleExecutionResponse = { _, _ ->
        error("rollbackExecution not configured")
    },
) : SqlConsoleApi {
    override suspend fun loadRuntimeContext(): RuntimeContext = runtimeContextHandler()

    override suspend fun loadInfo(): SqlConsoleInfo = infoHandler()

    override suspend fun loadState(workspaceId: String?): SqlConsoleStateSnapshot = loadStateHandler(workspaceId)

    override suspend fun saveState(
        request: SqlConsoleStateUpdate,
        workspaceId: String?,
    ): SqlConsoleStateSnapshot = saveStateHandler(request, workspaceId)

    override suspend fun saveSettings(request: SqlConsoleSettingsUpdate): SqlConsoleInfo = saveSettingsHandler(request)

    override suspend fun checkConnections(): SqlConsoleConnectionCheckResponse = checkConnectionsHandler()

    override suspend fun searchObjects(request: SqlConsoleObjectSearchRequest): SqlConsoleObjectSearchResponse =
        searchObjectsHandler(request)

    override suspend fun inspectObject(request: SqlConsoleObjectInspectorRequest): SqlConsoleObjectInspectorResponse =
        inspectObjectHandler(request)

    override suspend fun loadObjectColumns(request: SqlConsoleObjectColumnsRequest): SqlConsoleObjectColumnsResponse =
        loadObjectColumnsHandler(request)

    override suspend fun startQuery(request: SqlConsoleQueryStartRequest): SqlConsoleStartQueryResponse =
        startQueryHandler(request)

    override suspend fun loadExecution(executionId: String): SqlConsoleExecutionResponse = loadExecutionHandler(executionId)

    override suspend fun heartbeatExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse = heartbeatExecutionHandler(executionId, request)

    override suspend fun releaseExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse = releaseExecutionHandler(executionId, request)

    override suspend fun cancelExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse = cancelExecutionHandler(executionId, request)

    override suspend fun commitExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse = commitExecutionHandler(executionId, request)

    override suspend fun rollbackExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse = rollbackExecutionHandler(executionId, request)
}

internal fun <T> runSuspend(block: suspend () -> T): T {
    var completed: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                completed = result
            }
        },
    )
    return completed!!.getOrThrow()
}
