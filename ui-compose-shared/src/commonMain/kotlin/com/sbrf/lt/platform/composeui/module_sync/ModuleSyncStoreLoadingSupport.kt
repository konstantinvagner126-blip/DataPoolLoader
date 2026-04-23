package com.sbrf.lt.platform.composeui.module_sync

internal class ModuleSyncStoreLoadingSupport(
    private val api: ModuleSyncApi,
) {
    private val runtimeSupport = ModuleSyncStoreRuntimeSupport(api)
    private val selectionSupport = ModuleSyncStoreSelectionSupport(api)

    suspend fun load(
        historyLimit: Int = 20,
        preferredRunId: String? = null,
        selectiveSyncVisible: Boolean = false,
        selectedModuleCodes: Set<String> = emptySet(),
        moduleSearchQuery: String = "",
    ): ModuleSyncPageState {
        val runtimeContextResult = runtimeSupport.loadRuntimeContext()
        val runtimeContext = runtimeContextResult.getOrNull()
        if (runtimeContext == null) {
            return runtimeSupport.buildRuntimeUnavailableState(
                errorMessage = runtimeContextResult.exceptionOrNull()?.message ?: "Не удалось загрузить экран импорта модулей.",
                historyLimit = historyLimit,
                selectiveSyncVisible = selectiveSyncVisible,
                selectedModuleCodes = selectedModuleCodes,
                moduleSearchQuery = moduleSearchQuery,
            )
        }

        val syncStateResult = runCatching { api.loadSyncState() }
        val filesModulesResult = runtimeSupport.loadAvailableFileModules()
        val availableFileModules = filesModulesResult.getOrNull()?.modules.orEmpty()
        val normalizedSelectedModuleCodes = runtimeSupport.normalizeSelectedModuleCodes(
            selectedModuleCodes = selectedModuleCodes,
            availableFileModules = availableFileModules,
        )
        val syncState = syncStateResult.getOrDefault(
            ModuleSyncStateResponse(message = "Не удалось загрузить состояние синхронизации."),
        )
        if (!runtimeSupport.isDatabaseMode(runtimeContext)) {
            return runtimeSupport.buildNonDatabaseState(
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
        val selectedRunId = selectionSupport.resolveSelectedRunId(runs, syncState, preferredRunId)
        val detailsResult = selectionSupport.loadSelectedRunDetails(selectedRunId)
        val details = detailsResult.getOrNull()
        val firstError = listOfNotNull(
            syncStateResult.exceptionOrNull()?.message,
            runsResult.exceptionOrNull()?.message,
            detailsResult.exceptionOrNull()?.message,
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
}
