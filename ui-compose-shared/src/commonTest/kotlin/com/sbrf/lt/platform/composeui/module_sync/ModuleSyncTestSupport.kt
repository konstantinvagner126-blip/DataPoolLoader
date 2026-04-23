package com.sbrf.lt.platform.composeui.module_sync

import com.sbrf.lt.platform.composeui.model.DatabaseConnectionStatus
import com.sbrf.lt.platform.composeui.model.FilesModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.ModuleCatalogDiagnostics
import com.sbrf.lt.platform.composeui.model.ModuleCatalogItem
import com.sbrf.lt.platform.composeui.model.ModuleMetadataDescriptor
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeActorState
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

internal fun sampleModuleSyncRuntimeContext(
    effectiveMode: ModuleStoreMode = ModuleStoreMode.DATABASE,
): RuntimeContext =
    RuntimeContext(
        requestedMode = effectiveMode,
        effectiveMode = effectiveMode,
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

internal fun sampleFileModuleCatalogItem(id: String): ModuleCatalogItem =
    ModuleCatalogItem(
        id = id,
        descriptor = ModuleMetadataDescriptor(
            title = id,
        ),
    )

internal fun sampleFilesModulesCatalog(moduleIds: List<String> = listOf("alpha", "beta")): FilesModulesCatalogResponse =
    FilesModulesCatalogResponse(
        appsRootStatus = com.sbrf.lt.platform.composeui.model.AppsRootStatus(
            mode = "ok",
            message = "ok",
        ),
        diagnostics = ModuleCatalogDiagnostics(),
        modules = moduleIds.map(::sampleFileModuleCatalogItem),
    )

internal fun sampleModuleSyncState(
    activeFullSyncId: String? = null,
    activeSingleSyncIds: List<String> = emptyList(),
): ModuleSyncStateResponse =
    ModuleSyncStateResponse(
        activeFullSync = activeFullSyncId?.let {
            ActiveModuleSyncRunResponse(
                syncRunId = it,
                scope = "FULL",
                startedAt = "2026-04-23T10:00:00Z",
            )
        },
        activeSingleSyncs = activeSingleSyncIds.map {
            ActiveModuleSyncRunResponse(
                syncRunId = it,
                scope = "SINGLE",
                startedAt = "2026-04-23T10:00:00Z",
                moduleCode = "module-$it",
            )
        },
    )

internal fun sampleModuleSyncRuns(runIds: List<String>): List<ModuleSyncRunSummaryResponse> =
    runIds.map { runId ->
        ModuleSyncRunSummaryResponse(
            syncRunId = runId,
            scope = "FULL",
            status = "RUNNING",
            startedAt = "2026-04-23T10:00:00Z",
        )
    }

internal fun sampleModuleSyncRunDetails(syncRunId: String): ModuleSyncRunDetailsResponse =
    ModuleSyncRunDetailsResponse(
        run = ModuleSyncRunSummaryResponse(
            syncRunId = syncRunId,
            scope = "FULL",
            status = "RUNNING",
            startedAt = "2026-04-23T10:00:00Z",
        ),
    )

internal class StubModuleSyncApi(
    private val runtimeContextHandler: suspend () -> RuntimeContext = { sampleModuleSyncRuntimeContext() },
    private val syncStateHandler: suspend () -> ModuleSyncStateResponse = { sampleModuleSyncState() },
    private val runsHandler: suspend (Int) -> ModuleSyncRunsResponse = {
        ModuleSyncRunsResponse(sampleModuleSyncRuns(listOf("run-1", "run-2")))
    },
    private val detailsHandler: suspend (String) -> ModuleSyncRunDetailsResponse = { runId ->
        sampleModuleSyncRunDetails(runId)
    },
    private val filesModulesCatalogHandler: suspend () -> FilesModulesCatalogResponse = {
        sampleFilesModulesCatalog()
    },
    private val syncAllHandler: suspend () -> SyncRunResultResponse = {
        error("syncAll not configured")
    },
    private val syncOneHandler: suspend (String) -> SyncRunResultResponse = {
        error("syncOne not configured")
    },
    private val syncSelectedHandler: suspend (List<String>) -> SyncRunResultResponse = {
        error("syncSelected not configured")
    },
) : ModuleSyncApi {
    var detailsLoads: Int = 0
        private set

    override suspend fun loadRuntimeContext(): RuntimeContext = runtimeContextHandler()

    override suspend fun loadSyncState(): ModuleSyncStateResponse = syncStateHandler()

    override suspend fun loadSyncRuns(limit: Int): ModuleSyncRunsResponse = runsHandler(limit)

    override suspend fun loadSyncRunDetails(syncRunId: String): ModuleSyncRunDetailsResponse {
        detailsLoads += 1
        return detailsHandler(syncRunId)
    }

    override suspend fun loadFilesModulesCatalog(): FilesModulesCatalogResponse = filesModulesCatalogHandler()

    override suspend fun syncAll(): SyncRunResultResponse = syncAllHandler()

    override suspend fun syncOne(moduleCode: String): SyncRunResultResponse = syncOneHandler(moduleCode)

    override suspend fun syncSelected(moduleCodes: List<String>): SyncRunResultResponse =
        syncSelectedHandler(moduleCodes)
}

internal class StubModuleSyncLoadStore(
    private val handler: suspend (
        historyLimit: Int,
        preferredRunId: String?,
        selectiveSyncVisible: Boolean,
        selectedModuleCodes: Set<String>,
        moduleSearchQuery: String,
    ) -> ModuleSyncPageState,
) : ModuleSyncLoadStore {
    override suspend fun load(
        historyLimit: Int,
        preferredRunId: String?,
        selectiveSyncVisible: Boolean,
        selectedModuleCodes: Set<String>,
        moduleSearchQuery: String,
    ): ModuleSyncPageState =
        handler(
            historyLimit,
            preferredRunId,
            selectiveSyncVisible,
            selectedModuleCodes,
            moduleSearchQuery,
        )
}

internal fun <T> runModuleSyncSuspend(block: suspend () -> T): T {
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
