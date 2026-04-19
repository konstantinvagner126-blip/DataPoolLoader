package com.sbrf.lt.platform.composeui.module_sync

class ModuleSyncStore(
    private val api: ModuleSyncApi,
) {
    private val loadingSupport = ModuleSyncStoreLoadingSupport(api)
    private val actionSupport = ModuleSyncStoreActionSupport(api, loadingSupport)

    suspend fun load(
        historyLimit: Int = 20,
        preferredRunId: String? = null,
        selectiveSyncVisible: Boolean = false,
        selectedModuleCodes: Set<String> = emptySet(),
        moduleSearchQuery: String = "",
    ): ModuleSyncPageState =
        loadingSupport.load(
            historyLimit = historyLimit,
            preferredRunId = preferredRunId,
            selectiveSyncVisible = selectiveSyncVisible,
            selectedModuleCodes = selectedModuleCodes,
            moduleSearchQuery = moduleSearchQuery,
        )

    fun startLoading(current: ModuleSyncPageState): ModuleSyncPageState =
        current.copy(loading = true, errorMessage = null, successMessage = null)

    fun updateHistoryLimit(
        current: ModuleSyncPageState,
        historyLimit: Int,
    ): ModuleSyncPageState =
        current.copy(historyLimit = historyLimit)

    fun updateModuleSearchQuery(
        current: ModuleSyncPageState,
        query: String,
    ): ModuleSyncPageState =
        current.copy(moduleSearchQuery = query)

    fun toggleSelectiveSync(current: ModuleSyncPageState): ModuleSyncPageState =
        current.copy(
            selectiveSyncVisible = !current.selectiveSyncVisible,
            errorMessage = null,
            successMessage = null,
        )

    fun toggleModuleSelection(
        current: ModuleSyncPageState,
        moduleCode: String,
    ): ModuleSyncPageState {
        val nextSelection = current.selectedModuleCodes.toMutableSet()
        if (!nextSelection.add(moduleCode)) {
            nextSelection.remove(moduleCode)
        }
        return current.copy(selectedModuleCodes = nextSelection)
    }

    fun selectAllModules(
        current: ModuleSyncPageState,
        moduleCodes: List<String>,
    ): ModuleSyncPageState =
        current.copy(selectedModuleCodes = current.selectedModuleCodes + moduleCodes)

    fun clearSelectedModules(current: ModuleSyncPageState): ModuleSyncPageState =
        current.copy(selectedModuleCodes = emptySet())

    suspend fun refresh(current: ModuleSyncPageState): ModuleSyncPageState =
        loadingSupport.refresh(current)

    suspend fun selectRun(
        current: ModuleSyncPageState,
        syncRunId: String,
    ): ModuleSyncPageState =
        loadingSupport.selectRun(current, syncRunId)

    suspend fun syncAll(current: ModuleSyncPageState): ModuleSyncPageState =
        actionSupport.syncAll(current)

    suspend fun syncOne(current: ModuleSyncPageState): ModuleSyncPageState =
        actionSupport.syncOne(current)

    suspend fun syncSelected(current: ModuleSyncPageState): ModuleSyncPageState =
        actionSupport.syncSelected(current)

    fun beginAction(
        current: ModuleSyncPageState,
        actionName: String,
    ): ModuleSyncPageState =
        current.copy(actionInProgress = actionName, errorMessage = null, successMessage = null)
}
