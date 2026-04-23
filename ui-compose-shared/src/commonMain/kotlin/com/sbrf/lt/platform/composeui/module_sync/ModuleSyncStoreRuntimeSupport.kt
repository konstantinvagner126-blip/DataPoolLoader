package com.sbrf.lt.platform.composeui.module_sync

import com.sbrf.lt.platform.composeui.model.FilesModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.ModuleCatalogItem
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeContext

internal class ModuleSyncStoreRuntimeSupport(
    private val api: ModuleSyncApi,
) {
    suspend fun loadRuntimeContext(): Result<RuntimeContext> =
        runCatching { api.loadRuntimeContext() }

    suspend fun loadAvailableFileModules(): Result<FilesModulesCatalogResponse> =
        runCatching { api.loadFilesModulesCatalog() }

    fun normalizeSelectedModuleCodes(
        selectedModuleCodes: Set<String>,
        availableFileModules: List<ModuleCatalogItem>,
    ): Set<String> =
        selectedModuleCodes.filterTo(linkedSetOf()) { selectedCode ->
            availableFileModules.any { it.id == selectedCode }
        }

    fun buildRuntimeUnavailableState(
        errorMessage: String,
        historyLimit: Int,
        selectiveSyncVisible: Boolean,
        selectedModuleCodes: Set<String>,
        moduleSearchQuery: String,
    ): ModuleSyncPageState =
        ModuleSyncPageState(
            loading = false,
            errorMessage = errorMessage,
            historyLimit = historyLimit,
            selectiveSyncVisible = selectiveSyncVisible,
            selectedModuleCodes = selectedModuleCodes,
            moduleSearchQuery = moduleSearchQuery,
        )

    fun buildNonDatabaseState(
        runtimeContext: RuntimeContext,
        syncState: ModuleSyncStateResponse,
        availableFileModules: List<ModuleCatalogItem>,
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
            historyLimit = historyLimit,
            selectiveSyncVisible = selectiveSyncVisible,
            selectedModuleCodes = selectedModuleCodes,
            moduleSearchQuery = moduleSearchQuery,
            errorMessage = errorMessage,
        )

    fun isDatabaseMode(runtimeContext: RuntimeContext): Boolean =
        runtimeContext.effectiveMode == ModuleStoreMode.DATABASE
}
