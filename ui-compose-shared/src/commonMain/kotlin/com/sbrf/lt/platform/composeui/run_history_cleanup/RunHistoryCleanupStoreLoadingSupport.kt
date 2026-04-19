package com.sbrf.lt.platform.composeui.run_history_cleanup

internal class RunHistoryCleanupStoreLoadingSupport(
    private val api: RunHistoryCleanupApi,
) {
    suspend fun load(
        disableSafeguard: Boolean = false,
        outputDisableSafeguard: Boolean = false,
    ): RunHistoryCleanupPageState {
        val runtimeContextResult = runCatching { api.loadRuntimeContext() }
        val runtimeContext = runtimeContextResult.getOrNull()
        if (runtimeContext == null) {
            return RunHistoryCleanupPageState(
                loading = false,
                cleanupDisableSafeguard = disableSafeguard,
                outputDisableSafeguard = outputDisableSafeguard,
                errorMessage = runtimeContextResult.exceptionOrNull()?.message
                    ?: "Не удалось загрузить экран очистки истории запусков.",
            )
        }

        val previewResult = runCatching { api.loadPreview(disableSafeguard) }
        val outputPreviewResult = runCatching { api.loadOutputPreview(outputDisableSafeguard) }
        return RunHistoryCleanupPageState(
            loading = false,
            runtimeContext = runtimeContext,
            cleanupDisableSafeguard = disableSafeguard,
            outputDisableSafeguard = outputDisableSafeguard,
            preview = previewResult.getOrNull(),
            outputPreview = outputPreviewResult.getOrNull(),
            errorMessage = listOfNotNull(
                previewResult.exceptionOrNull()?.message,
                outputPreviewResult.exceptionOrNull()?.message,
            ).distinct().takeIf { it.isNotEmpty() }?.joinToString("\n"),
        )
    }

    suspend fun refresh(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        load(
            disableSafeguard = current.cleanupDisableSafeguard,
            outputDisableSafeguard = current.outputDisableSafeguard,
        )
}
