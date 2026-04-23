package com.sbrf.lt.platform.composeui.module_runs

import com.sbrf.lt.platform.composeui.model.DatabaseConnectionStatus
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeActorState
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

internal fun sampleModuleRunsRuntimeContext(
    effectiveMode: ModuleStoreMode = ModuleStoreMode.FILES,
    fallbackReason: String? = null,
): RuntimeContext =
    RuntimeContext(
        requestedMode = effectiveMode,
        effectiveMode = effectiveMode,
        fallbackReason = fallbackReason,
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

internal fun sampleModuleRunHistory(
    activeRunId: String? = null,
    runIds: List<String> = listOf("run-1", "run-2"),
): ModuleRunHistoryResponse =
    ModuleRunHistoryResponse(
        storageMode = "files",
        moduleId = "module-a",
        activeRunId = activeRunId,
        runs = runIds.map { runId ->
            ModuleRunSummaryResponse(
                runId = runId,
                moduleId = "module-a",
                moduleTitle = "Module A",
                status = "SUCCESS",
                startedAt = "2026-04-23T10:00:00Z",
            )
        },
    )

internal fun sampleModuleRunDetails(runId: String): ModuleRunDetailsResponse =
    ModuleRunDetailsResponse(
        run = ModuleRunSummaryResponse(
            runId = runId,
            moduleId = "module-a",
            moduleTitle = "Module A",
            status = "SUCCESS",
            startedAt = "2026-04-23T10:00:00Z",
        ),
    )

internal class StubModuleRunsApi(
    private val runtimeContextHandler: suspend () -> RuntimeContext = { sampleModuleRunsRuntimeContext() },
    private val sessionHandler: suspend (String, String) -> ModuleRunPageSessionResponse = { storage, moduleId ->
        ModuleRunPageSessionResponse(
            storageMode = storage,
            moduleId = moduleId,
            moduleTitle = "Module A",
            moduleMeta = "{}",
        )
    },
    private val historyHandler: suspend (String, String, Int) -> ModuleRunHistoryResponse = { _, _, _ ->
        sampleModuleRunHistory()
    },
    private val detailsHandler: suspend (String, String, String) -> ModuleRunDetailsResponse = { _, _, runId ->
        sampleModuleRunDetails(runId)
    },
) : ModuleRunsApi {
    var runtimeContextLoads: Int = 0
        private set

    var runDetailsLoads: Int = 0
        private set

    override suspend fun loadRuntimeContext(): RuntimeContext {
        runtimeContextLoads += 1
        return runtimeContextHandler()
    }

    override suspend fun loadSession(storage: String, moduleId: String): ModuleRunPageSessionResponse =
        sessionHandler(storage, moduleId)

    override suspend fun loadHistory(
        storage: String,
        moduleId: String,
        limit: Int,
    ): ModuleRunHistoryResponse = historyHandler(storage, moduleId, limit)

    override suspend fun loadRunDetails(
        storage: String,
        moduleId: String,
        runId: String,
    ): ModuleRunDetailsResponse {
        runDetailsLoads += 1
        return detailsHandler(storage, moduleId, runId)
    }
}

internal fun <T> runModuleRunsSuspend(block: suspend () -> T): T {
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
