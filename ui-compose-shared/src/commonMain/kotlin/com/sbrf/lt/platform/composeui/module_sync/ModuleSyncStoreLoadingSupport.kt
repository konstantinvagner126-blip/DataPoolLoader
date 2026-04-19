package com.sbrf.lt.platform.composeui.module_sync

import com.sbrf.lt.platform.composeui.model.ModuleStoreMode

internal class ModuleSyncStoreLoadingSupport(
    private val api: ModuleSyncApi,
) {
    suspend fun load(
        historyLimit: Int = 20,
        preferredRunId: String? = null,
        selectiveSyncVisible: Boolean = false,
        selectedModuleCodes: Set<String> = emptySet(),
        moduleSearchQuery: String = "",
    ): ModuleSyncPageState {
        val runtimeContextResult = runCatching { api.loadRuntimeContext() }
        val runtimeContext = runtimeContextResult.getOrNull()
        if (runtimeContext == null) {
            return ModuleSyncPageState(
                loading = false,
                errorMessage = runtimeContextResult.exceptionOrNull()?.message ?: "Не удалось загрузить экран импорта модулей.",
                historyLimit = historyLimit,
                selectiveSyncVisible = selectiveSyncVisible,
                selectedModuleCodes = selectedModuleCodes,
                moduleSearchQuery = moduleSearchQuery,
            )
        }

        val syncStateResult = runCatching { api.loadSyncState() }
        val filesModulesResult = runCatching { api.loadFilesModulesCatalog() }
        val availableFileModules = filesModulesResult.getOrNull()?.modules.orEmpty()
        val normalizedSelectedModuleCodes = selectedModuleCodes.filterTo(linkedSetOf()) { selectedCode ->
            availableFileModules.any { it.id == selectedCode }
        }
        val syncState = syncStateResult.getOrDefault(
            ModuleSyncStateResponse(message = "Не удалось загрузить состояние синхронизации."),
        )
        if (runtimeContext.effectiveMode != ModuleStoreMode.DATABASE) {
            return ModuleSyncPageState(
                loading = false,
                runtimeContext = runtimeContext,
                syncState = syncState,
                availableFileModules = availableFileModules,
                historyLimit = historyLimit,
                selectiveSyncVisible = selectiveSyncVisible,
                selectedModuleCodes = normalizedSelectedModuleCodes,
                moduleSearchQuery = moduleSearchQuery,
                errorMessage = listOfNotNull(
                    syncStateResult.exceptionOrNull()?.message,
                    filesModulesResult.exceptionOrNull()?.message,
                ).firstOrNull(),
            )
        }

        val runsResult = runCatching { api.loadSyncRuns(historyLimit).runs }
        val runs = runsResult.getOrDefault(emptyList())
        val selectedRunId = resolveSelectedRunId(runs, syncState, preferredRunId)
        val detailsResult = selectedRunId?.let { runId ->
            runCatching { api.loadSyncRunDetails(runId) }
        }
        val details = detailsResult?.getOrNull()
        val firstError = listOfNotNull(
            syncStateResult.exceptionOrNull()?.message,
            runsResult.exceptionOrNull()?.message,
            detailsResult?.exceptionOrNull()?.message,
        ).firstOrNull()
        return ModuleSyncPageState(
            loading = false,
            runtimeContext = runtimeContext,
            syncState = syncState,
            availableFileModules = availableFileModules,
            runs = runs,
            selectedRunId = selectedRunId,
            selectedRunDetails = details,
            historyLimit = historyLimit,
            selectiveSyncVisible = selectiveSyncVisible,
            selectedModuleCodes = normalizedSelectedModuleCodes,
            moduleSearchQuery = moduleSearchQuery,
            errorMessage = firstError,
        )
    }

    suspend fun refresh(current: ModuleSyncPageState): ModuleSyncPageState =
        load(
            historyLimit = current.historyLimit,
            preferredRunId = current.selectedRunId,
            selectiveSyncVisible = current.selectiveSyncVisible,
            selectedModuleCodes = current.selectedModuleCodes,
            moduleSearchQuery = current.moduleSearchQuery,
        )

    suspend fun selectRun(
        current: ModuleSyncPageState,
        syncRunId: String,
    ): ModuleSyncPageState =
        runCatching {
            val details = api.loadSyncRunDetails(syncRunId)
            current.copy(
                loading = false,
                errorMessage = null,
                selectedRunId = syncRunId,
                selectedRunDetails = details,
            )
        }.getOrElse { error ->
            current.copy(
                loading = false,
                errorMessage = error.message ?: "Не удалось загрузить детали импорта.",
            )
        }

    private fun resolveSelectedRunId(
        runs: List<ModuleSyncRunSummaryResponse>,
        syncState: ModuleSyncStateResponse,
        preferredRunId: String?,
    ): String? =
        when {
            preferredRunId != null && runs.any { it.syncRunId == preferredRunId } -> preferredRunId
            syncState.activeFullSync != null && runs.any { it.syncRunId == syncState.activeFullSync.syncRunId } ->
                syncState.activeFullSync.syncRunId
            syncState.activeSingleSyncs.isNotEmpty() && runs.any { it.syncRunId == syncState.activeSingleSyncs.first().syncRunId } ->
                syncState.activeSingleSyncs.first().syncRunId
            else -> runs.firstOrNull()?.syncRunId
        }
}
