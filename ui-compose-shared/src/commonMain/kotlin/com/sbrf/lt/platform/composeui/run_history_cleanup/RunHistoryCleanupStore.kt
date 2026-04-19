package com.sbrf.lt.platform.composeui.run_history_cleanup

class RunHistoryCleanupStore(
    private val api: RunHistoryCleanupApi,
) {
    private val loadingSupport = RunHistoryCleanupStoreLoadingSupport(api)
    private val actionSupport = RunHistoryCleanupStoreActionSupport(api, loadingSupport)

    suspend fun load(
        disableSafeguard: Boolean = false,
        outputDisableSafeguard: Boolean = false,
    ): RunHistoryCleanupPageState =
        loadingSupport.load(disableSafeguard, outputDisableSafeguard)

    fun startLoading(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        current.copy(loading = true, errorMessage = null, successMessage = null)

    fun beginAction(
        current: RunHistoryCleanupPageState,
        actionName: String,
    ): RunHistoryCleanupPageState =
        current.copy(actionInProgress = actionName, errorMessage = null, successMessage = null)

    fun updateCleanupSafeguard(
        current: RunHistoryCleanupPageState,
        disableSafeguard: Boolean,
    ): RunHistoryCleanupPageState =
        current.copy(cleanupDisableSafeguard = disableSafeguard)

    suspend fun refresh(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        loadingSupport.refresh(current)

    suspend fun refreshPreview(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        actionSupport.refreshPreview(current)

    fun updateOutputSafeguard(
        current: RunHistoryCleanupPageState,
        disableSafeguard: Boolean,
    ): RunHistoryCleanupPageState =
        current.copy(outputDisableSafeguard = disableSafeguard)

    suspend fun refreshOutputPreview(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        actionSupport.refreshOutputPreview(current)

    suspend fun cleanupRunHistory(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        actionSupport.cleanupRunHistory(current)

    suspend fun cleanupOutputs(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        actionSupport.cleanupOutputs(current)
}
