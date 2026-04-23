package com.sbrf.lt.platform.composeui.run_history_cleanup

import com.sbrf.lt.platform.composeui.model.RuntimeContext

internal class RunHistoryCleanupStoreLoadingStateSupport {
    fun createRuntimeUnavailableState(
        disableSafeguard: Boolean,
        outputDisableSafeguard: Boolean,
        errorMessage: String,
    ): RunHistoryCleanupPageState =
        RunHistoryCleanupPageState(
            loading = false,
            cleanupDisableSafeguard = disableSafeguard,
            outputDisableSafeguard = outputDisableSafeguard,
            errorMessage = errorMessage,
        )

    fun createLoadedState(
        runtimeContext: RuntimeContext,
        disableSafeguard: Boolean,
        outputDisableSafeguard: Boolean,
        preview: RunHistoryCleanupPreviewResponse?,
        outputPreview: OutputRetentionPreviewResponse?,
        previewErrorMessage: String?,
        outputPreviewErrorMessage: String?,
    ): RunHistoryCleanupPageState =
        RunHistoryCleanupPageState(
            loading = false,
            runtimeContext = runtimeContext,
            cleanupDisableSafeguard = disableSafeguard,
            outputDisableSafeguard = outputDisableSafeguard,
            preview = preview,
            outputPreview = outputPreview,
            errorMessage = combineErrorMessages(previewErrorMessage, outputPreviewErrorMessage),
        )

    private fun combineErrorMessages(
        previewErrorMessage: String?,
        outputPreviewErrorMessage: String?,
    ): String? =
        listOfNotNull(
            previewErrorMessage,
            outputPreviewErrorMessage,
        ).distinct().takeIf { it.isNotEmpty() }?.joinToString("\n")
}
