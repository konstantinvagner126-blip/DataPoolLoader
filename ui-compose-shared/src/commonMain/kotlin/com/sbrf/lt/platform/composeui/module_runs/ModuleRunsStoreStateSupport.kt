package com.sbrf.lt.platform.composeui.module_runs

import com.sbrf.lt.platform.composeui.model.RuntimeContext

internal class ModuleRunsStoreStateSupport {
    fun createLoadedState(
        runtimeContext: RuntimeContext?,
        session: ModuleRunPageSessionResponse,
        history: ModuleRunHistoryResponse,
        selectedRunId: String?,
        selectedRunDetails: ModuleRunDetailsResponse?,
        historyLimit: Int,
    ): ModuleRunsPageState =
        ModuleRunsPageState(
            loading = false,
            errorMessage = null,
            runtimeContext = runtimeContext,
            session = session,
            history = history,
            selectedRunId = selectedRunId,
            selectedRunDetails = selectedRunDetails,
            historyLimit = historyLimit,
        )

    fun applySelectedRun(
        current: ModuleRunsPageState,
        runtimeContext: RuntimeContext?,
        runId: String,
        details: ModuleRunDetailsResponse?,
    ): ModuleRunsPageState =
        current.copy(
            loading = false,
            errorMessage = null,
            runtimeContext = runtimeContext,
            selectedRunId = runId,
            selectedRunDetails = details,
        )

    fun applyReloadedHistory(
        current: ModuleRunsPageState,
        runtimeContext: RuntimeContext?,
        history: ModuleRunHistoryResponse,
        selectedRunId: String?,
        selectedRunDetails: ModuleRunDetailsResponse?,
    ): ModuleRunsPageState =
        current.copy(
            loading = false,
            errorMessage = null,
            runtimeContext = runtimeContext,
            history = history,
            selectedRunId = selectedRunId,
            selectedRunDetails = selectedRunDetails,
        )

    fun applyInitialLoadFailure(
        historyLimit: Int,
        error: Throwable,
    ): ModuleRunsPageState =
        ModuleRunsPageState(
            loading = false,
            errorMessage = error.message ?: "Не удалось загрузить историю запусков.",
            historyLimit = historyLimit,
        )

    fun applySelectRunFailure(
        current: ModuleRunsPageState,
        error: Throwable,
    ): ModuleRunsPageState =
        current.copy(
            loading = false,
            errorMessage = error.message ?: "Не удалось загрузить подробности запуска.",
        )

    fun applyReloadFailure(
        current: ModuleRunsPageState,
        error: Throwable,
    ): ModuleRunsPageState =
        current.copy(
            loading = false,
            errorMessage = error.message ?: "Не удалось обновить историю запусков.",
        )
}
