package com.sbrf.lt.platform.composeui.run_history_cleanup

internal class RunHistoryCleanupStoreActionSupport(
    private val api: RunHistoryCleanupApi,
    private val loadingSupport: RunHistoryCleanupStoreLoadingSupport,
) {
    suspend fun refreshPreview(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        runCatching {
            val preview = api.loadPreview(current.cleanupDisableSafeguard)
            current.copy(
                loading = false,
                actionInProgress = null,
                errorMessage = null,
                preview = preview,
            )
        }.getOrElse { error ->
            current.copy(
                loading = false,
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось загрузить preview очистки истории запусков.",
            )
        }

    suspend fun refreshOutputPreview(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        runCatching {
            val preview = api.loadOutputPreview(current.outputDisableSafeguard)
            current.copy(
                loading = false,
                actionInProgress = null,
                errorMessage = null,
                outputPreview = preview,
            )
        }.getOrElse { error ->
            current.copy(
                loading = false,
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось загрузить preview retention output-каталогов.",
            )
        }

    suspend fun cleanupRunHistory(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        runCatching {
            val result = api.cleanupRunHistory(current.cleanupDisableSafeguard)
            loadingSupport.refresh(current).copy(
                successMessage = if (result.totalRunsDeleted > 0 || result.totalOrphanExecutionSnapshotsDeleted > 0) {
                    "Очистка завершена: удалено ${result.totalRunsDeleted} запусков."
                } else {
                    "Очистка завершена: удалять было нечего."
                },
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
                successMessage = if (result.totalOutputDirsDeleted > 0 || result.totalMissingOutputDirs > 0) {
                    "Очистка output завершена: удалено ${result.totalOutputDirsDeleted} каталогов."
                } else {
                    "Очистка output завершена: удалять было нечего."
                },
                actionInProgress = null,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось выполнить retention output-каталогов.",
            )
        }
}
