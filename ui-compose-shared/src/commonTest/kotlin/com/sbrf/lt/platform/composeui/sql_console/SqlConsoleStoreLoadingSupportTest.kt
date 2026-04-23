package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.model.DatabaseConnectionStatus
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeActorState
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlConsoleStoreLoadingSupportTest {

    @Test
    fun `console load restores selected group and manual inclusion for ungrouped source`() {
        val support = SqlConsoleStoreLoadingSupport(
            FakeSqlConsoleApi(
                info = sqlConsoleInfo(),
                state = SqlConsoleStateSnapshot(
                    draftSql = "select * from demo",
                    selectedGroupNames = listOf("dev"),
                    selectedSourceNames = listOf("db1", "db2", "db3"),
                    pageSize = 100,
                    strictSafetyEnabled = true,
                    transactionMode = "transaction_per_shard",
                ),
            ),
        )

        val state = runSuspend { support.load("workspace-a") }

        assertEquals(listOf("dev"), state.selectedGroupNames)
        assertEquals(listOf("db1", "db2", "db3"), state.selectedSourceNames)
        assertEquals(listOf("db3"), state.manuallyIncludedSourceNames)
        assertEquals(emptyList(), state.manuallyExcludedSourceNames)
        assertEquals(100, state.pageSize)
        assertEquals(true, state.strictSafetyEnabled)
        assertEquals("TRANSACTION_PER_SHARD", state.transactionMode)
        assertEquals("select * from demo", state.draftSql)
    }

    @Test
    fun `objects load restores manual exclusion and ignores unknown persisted source`() {
        val support = SqlConsoleObjectsStoreLoadingSupport(
            FakeSqlConsoleApi(
                info = sqlConsoleInfo(),
                state = SqlConsoleStateSnapshot(
                    draftSql = "select * from demo",
                    selectedGroupNames = listOf("dev"),
                    selectedSourceNames = listOf("db1", "missing"),
                ),
            ),
        )

        val state = runSuspend { support.load("workspace-b") }

        assertEquals(listOf("dev"), state.selectedGroupNames)
        assertEquals(listOf("db1"), state.selectedSourceNames)
        assertEquals(emptyList(), state.manuallyIncludedSourceNames)
        assertEquals(listOf("db2"), state.manuallyExcludedSourceNames)
    }
}

private class FakeSqlConsoleApi(
    private val runtimeContext: RuntimeContext = RuntimeContext(
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
    ),
    private val info: SqlConsoleInfo,
    private val state: SqlConsoleStateSnapshot,
) : SqlConsoleApi {
    override suspend fun loadRuntimeContext(): RuntimeContext = runtimeContext

    override suspend fun loadInfo(): SqlConsoleInfo = info

    override suspend fun loadState(workspaceId: String?): SqlConsoleStateSnapshot = state

    override suspend fun saveState(
        request: SqlConsoleStateUpdate,
        workspaceId: String?,
    ): SqlConsoleStateSnapshot = error("not used")

    override suspend fun saveSettings(request: SqlConsoleSettingsUpdate): SqlConsoleInfo = error("not used")

    override suspend fun checkConnections(): SqlConsoleConnectionCheckResponse = error("not used")

    override suspend fun searchObjects(request: SqlConsoleObjectSearchRequest): SqlConsoleObjectSearchResponse =
        error("not used")

    override suspend fun inspectObject(request: SqlConsoleObjectInspectorRequest): SqlConsoleObjectInspectorResponse =
        error("not used")

    override suspend fun loadObjectColumns(request: SqlConsoleObjectColumnsRequest): SqlConsoleObjectColumnsResponse =
        error("not used")

    override suspend fun startQuery(request: SqlConsoleQueryStartRequest): SqlConsoleStartQueryResponse =
        error("not used")

    override suspend fun loadExecution(executionId: String): SqlConsoleExecutionResponse = error("not used")

    override suspend fun heartbeatExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse = error("not used")

    override suspend fun releaseExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse = error("not used")

    override suspend fun cancelExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse = error("not used")

    override suspend fun commitExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse = error("not used")

    override suspend fun rollbackExecution(
        executionId: String,
        request: SqlConsoleExecutionOwnerActionRequest,
    ): SqlConsoleExecutionResponse = error("not used")
}

private fun sqlConsoleInfo(): SqlConsoleInfo =
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

private fun <T> runSuspend(block: suspend () -> T): T {
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
