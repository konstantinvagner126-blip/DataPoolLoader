package com.sbrf.lt.platform.composeui.run_history_cleanup

class RunHistoryCleanupStore(
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
            ).takeIf { it.isNotEmpty() }?.joinToString("\n"),
        )
    }

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
        load(
            disableSafeguard = current.cleanupDisableSafeguard,
            outputDisableSafeguard = current.outputDisableSafeguard,
        )

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

    fun updateOutputSafeguard(
        current: RunHistoryCleanupPageState,
        disableSafeguard: Boolean,
    ): RunHistoryCleanupPageState =
        current.copy(outputDisableSafeguard = disableSafeguard)

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
            load(
                disableSafeguard = current.cleanupDisableSafeguard,
                outputDisableSafeguard = current.outputDisableSafeguard,
            ).copy(
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
            load(
                disableSafeguard = current.cleanupDisableSafeguard,
                outputDisableSafeguard = current.outputDisableSafeguard,
            ).copy(
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
