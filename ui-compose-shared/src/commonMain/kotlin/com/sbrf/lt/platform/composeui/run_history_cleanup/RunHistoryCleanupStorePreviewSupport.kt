package com.sbrf.lt.platform.composeui.run_history_cleanup

internal class RunHistoryCleanupStorePreviewSupport(
    private val api: RunHistoryCleanupApi,
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
}
