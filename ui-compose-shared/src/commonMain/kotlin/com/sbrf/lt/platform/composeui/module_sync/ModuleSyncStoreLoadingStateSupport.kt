package com.sbrf.lt.platform.composeui.module_sync

import com.sbrf.lt.platform.composeui.model.ModuleCatalogItem
import com.sbrf.lt.platform.composeui.model.RuntimeContext

internal class ModuleSyncStoreLoadingStateSupport {
    fun createDatabaseLoadedState(
        runtimeContext: RuntimeContext,
        syncState: ModuleSyncStateResponse,
        availableFileModules: List<ModuleCatalogItem>,
        runs: List<ModuleSyncRunSummaryResponse>,
        selectedRunId: String?,
        selectedRunDetails: ModuleSyncRunDetailsResponse?,
        historyLimit: Int,
        selectiveSyncVisible: Boolean,
        selectedModuleCodes: Set<String>,
        moduleSearchQuery: String,
        errorMessage: String?,
    ): ModuleSyncPageState =
        ModuleSyncPageState(
            loading = false,
            runtimeContext = runtimeContext,
            syncState = syncState,
            availableFileModules = availableFileModules,
            runs = runs,
            selectedRunId = selectedRunId,
            selectedRunDetails = selectedRunDetails,
            historyLimit = historyLimit,
            selectiveSyncVisible = selectiveSyncVisible,
            selectedModuleCodes = selectedModuleCodes,
            moduleSearchQuery = moduleSearchQuery,
            errorMessage = errorMessage,
        )

    fun applySelectedRun(
        current: ModuleSyncPageState,
        syncRunId: String,
        details: ModuleSyncRunDetailsResponse,
    ): ModuleSyncPageState =
        current.copy(
            loading = false,
            errorMessage = null,
            selectedRunId = syncRunId,
            selectedRunDetails = details,
        )

    fun applySelectRunFailure(
        current: ModuleSyncPageState,
        error: Throwable,
    ): ModuleSyncPageState =
        current.copy(
            loading = false,
            errorMessage = error.message ?: "Не удалось загрузить детали импорта.",
        )
}
