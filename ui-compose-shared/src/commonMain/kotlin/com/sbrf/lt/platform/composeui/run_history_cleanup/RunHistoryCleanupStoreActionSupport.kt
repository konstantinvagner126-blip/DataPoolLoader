package com.sbrf.lt.platform.composeui.run_history_cleanup

internal class RunHistoryCleanupStoreActionSupport(
    api: RunHistoryCleanupApi,
    loadingSupport: RunHistoryCleanupStoreLoadingSupport,
) {
    private val previewSupport = RunHistoryCleanupStorePreviewSupport(api)
    private val executionSupport = RunHistoryCleanupStoreExecutionSupport(api, loadingSupport)

    suspend fun refreshOutputPreview(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        previewSupport.refreshOutputPreview(current)

    suspend fun refreshPreview(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        previewSupport.refreshPreview(current)

    suspend fun cleanupRunHistory(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        executionSupport.cleanupRunHistory(current)

    suspend fun cleanupOutputs(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        executionSupport.cleanupOutputs(current)
}
