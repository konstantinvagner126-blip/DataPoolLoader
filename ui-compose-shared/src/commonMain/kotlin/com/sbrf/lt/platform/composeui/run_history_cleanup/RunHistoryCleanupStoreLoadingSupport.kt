package com.sbrf.lt.platform.composeui.run_history_cleanup

internal class RunHistoryCleanupStoreLoadingSupport(
    private val api: RunHistoryCleanupApi,
) {
    private val stateSupport = RunHistoryCleanupStoreLoadingStateSupport()

    suspend fun load(
        disableSafeguard: Boolean = false,
        outputDisableSafeguard: Boolean = false,
    ): RunHistoryCleanupPageState {
        val runtimeContextResult = runCatching { api.loadRuntimeContext() }
        val runtimeContext = runtimeContextResult.getOrNull()
        if (runtimeContext == null) {
            return stateSupport.createRuntimeUnavailableState(
                disableSafeguard = disableSafeguard,
                outputDisableSafeguard = outputDisableSafeguard,
                errorMessage = runtimeContextResult.exceptionOrNull()?.message
                    ?: "Не удалось загрузить экран очистки истории запусков.",
            )
        }

        val previewResult = runCatching { api.loadPreview(disableSafeguard) }
        val outputPreviewResult = runCatching { api.loadOutputPreview(outputDisableSafeguard) }
        return stateSupport.createLoadedState(
            runtimeContext = runtimeContext,
            disableSafeguard = disableSafeguard,
            outputDisableSafeguard = outputDisableSafeguard,
            preview = previewResult.getOrNull(),
            outputPreview = outputPreviewResult.getOrNull(),
            previewErrorMessage = previewResult.exceptionOrNull()?.message,
            outputPreviewErrorMessage = outputPreviewResult.exceptionOrNull()?.message,
        )
    }

    suspend fun refresh(current: RunHistoryCleanupPageState): RunHistoryCleanupPageState =
        load(
            disableSafeguard = current.cleanupDisableSafeguard,
            outputDisableSafeguard = current.outputDisableSafeguard,
        )
}
