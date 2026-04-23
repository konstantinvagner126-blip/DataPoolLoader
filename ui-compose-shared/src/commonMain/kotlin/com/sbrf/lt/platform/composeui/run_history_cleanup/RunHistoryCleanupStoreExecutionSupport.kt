package com.sbrf.lt.platform.composeui.run_history_cleanup

internal class RunHistoryCleanupStoreExecutionSupport(
    private val api: RunHistoryCleanupApi,
    private val loadingSupport: RunHistoryCleanupStoreLoadingSupport,
    private val messageSupport: RunHistoryCleanupStoreExecutionMessageSupport = RunHistoryCleanupStoreExecutionMessageSupport(),
) {
    suspend fun cleanupRunHistory(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        runCatching {
            val result = api.cleanupRunHistory(current.cleanupDisableSafeguard)
            loadingSupport.refresh(current).copy(
                successMessage = messageSupport.runHistorySuccessMessage(result),
                actionInProgress = null,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось выполнить очистку истории запусков.",
            )
        }

    suspend fun cleanupOutputs(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        runCatching {
            val result = api.cleanupOutputs(current.outputDisableSafeguard)
            loadingSupport.refresh(current).copy(
                successMessage = messageSupport.outputSuccessMessage(result),
                actionInProgress = null,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось выполнить retention output-каталогов.",
            )
        }
}
