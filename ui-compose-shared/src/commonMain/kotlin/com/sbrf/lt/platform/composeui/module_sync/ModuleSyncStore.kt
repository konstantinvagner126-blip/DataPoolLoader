package com.sbrf.lt.platform.composeui.module_sync

import com.sbrf.lt.platform.composeui.model.ModuleStoreMode

class ModuleSyncStore(
    private val api: ModuleSyncApi,
) {
    suspend fun load(
        historyLimit: Int = 20,
        preferredRunId: String? = null,
        selectiveSyncVisible: Boolean = false,
        selectedModuleCodes: Set<String> = emptySet(),
        moduleSearchQuery: String = "",
    ): ModuleSyncPageState {
        val runtimeContextResult = runCatching { api.loadRuntimeContext() }
        val runtimeContext = runtimeContextResult.getOrNull()
        if (runtimeContext == null) {
            return ModuleSyncPageState(
                loading = false,
                errorMessage = runtimeContextResult.exceptionOrNull()?.message ?: "Не удалось загрузить экран импорта модулей.",
                historyLimit = historyLimit,
                selectiveSyncVisible = selectiveSyncVisible,
                selectedModuleCodes = selectedModuleCodes,
                moduleSearchQuery = moduleSearchQuery,
            )
        }

        val syncStateResult = runCatching { api.loadSyncState() }
        val filesModulesResult = runCatching { api.loadFilesModulesCatalog() }
        val availableFileModules = filesModulesResult.getOrNull()?.modules.orEmpty()
        val normalizedSelectedModuleCodes = selectedModuleCodes.filterTo(linkedSetOf()) { selectedCode ->
            availableFileModules.any { it.id == selectedCode }
        }
        val syncState = syncStateResult.getOrDefault(
            ModuleSyncStateResponse(message = "Не удалось загрузить состояние синхронизации."),
        )
        if (runtimeContext.effectiveMode != ModuleStoreMode.DATABASE) {
            return ModuleSyncPageState(
                loading = false,
                runtimeContext = runtimeContext,
                syncState = syncState,
                availableFileModules = availableFileModules,
                historyLimit = historyLimit,
                selectiveSyncVisible = selectiveSyncVisible,
                selectedModuleCodes = normalizedSelectedModuleCodes,
                moduleSearchQuery = moduleSearchQuery,
                errorMessage = listOfNotNull(
                    syncStateResult.exceptionOrNull()?.message,
                    filesModulesResult.exceptionOrNull()?.message,
                ).firstOrNull(),
            )
        }

        val runsResult = runCatching { api.loadSyncRuns(historyLimit).runs }
        val runs = runsResult.getOrDefault(emptyList())
        val selectedRunId = resolveSelectedRunId(runs, syncState, preferredRunId)
        val detailsResult = selectedRunId?.let { runId ->
            runCatching { api.loadSyncRunDetails(runId) }
        }
        val details = detailsResult?.getOrNull()
        val firstError = listOfNotNull(
            syncStateResult.exceptionOrNull()?.message,
            runsResult.exceptionOrNull()?.message,
            detailsResult?.exceptionOrNull()?.message,
        ).firstOrNull()
        return ModuleSyncPageState(
            loading = false,
            runtimeContext = runtimeContext,
            syncState = syncState,
            availableFileModules = availableFileModules,
            runs = runs,
            selectedRunId = selectedRunId,
            selectedRunDetails = details,
            historyLimit = historyLimit,
            selectiveSyncVisible = selectiveSyncVisible,
            selectedModuleCodes = normalizedSelectedModuleCodes,
            moduleSearchQuery = moduleSearchQuery,
            errorMessage = firstError,
        )
    }

    fun startLoading(current: ModuleSyncPageState): ModuleSyncPageState =
        current.copy(loading = true, errorMessage = null, successMessage = null)

    fun updateHistoryLimit(
        current: ModuleSyncPageState,
        historyLimit: Int,
    ): ModuleSyncPageState =
        current.copy(historyLimit = historyLimit)

    fun updateModuleSearchQuery(
        current: ModuleSyncPageState,
        query: String,
    ): ModuleSyncPageState =
        current.copy(moduleSearchQuery = query)

    fun toggleSelectiveSync(current: ModuleSyncPageState): ModuleSyncPageState =
        current.copy(
            selectiveSyncVisible = !current.selectiveSyncVisible,
            errorMessage = null,
            successMessage = null,
        )

    fun toggleModuleSelection(
        current: ModuleSyncPageState,
        moduleCode: String,
    ): ModuleSyncPageState {
        val nextSelection = current.selectedModuleCodes.toMutableSet()
        if (!nextSelection.add(moduleCode)) {
            nextSelection.remove(moduleCode)
        }
        return current.copy(selectedModuleCodes = nextSelection)
    }

    fun selectAllModules(
        current: ModuleSyncPageState,
        moduleCodes: List<String>,
    ): ModuleSyncPageState =
        current.copy(selectedModuleCodes = current.selectedModuleCodes + moduleCodes)

    fun clearSelectedModules(current: ModuleSyncPageState): ModuleSyncPageState =
        current.copy(selectedModuleCodes = emptySet())

    suspend fun refresh(current: ModuleSyncPageState): ModuleSyncPageState =
        load(
            historyLimit = current.historyLimit,
            preferredRunId = current.selectedRunId,
            selectiveSyncVisible = current.selectiveSyncVisible,
            selectedModuleCodes = current.selectedModuleCodes,
            moduleSearchQuery = current.moduleSearchQuery,
        )

    suspend fun selectRun(
        current: ModuleSyncPageState,
        syncRunId: String,
    ): ModuleSyncPageState =
        runCatching {
            val details = api.loadSyncRunDetails(syncRunId)
            current.copy(
                loading = false,
                errorMessage = null,
                selectedRunId = syncRunId,
                selectedRunDetails = details,
            )
        }.getOrElse { error ->
            current.copy(
                loading = false,
                errorMessage = error.message ?: "Не удалось загрузить детали импорта.",
            )
        }

    suspend fun syncAll(current: ModuleSyncPageState): ModuleSyncPageState =
        runCatching {
            val result = api.syncAll()
            load(
                historyLimit = current.historyLimit,
                preferredRunId = result.syncRunId,
                selectiveSyncVisible = current.selectiveSyncVisible,
                selectedModuleCodes = current.selectedModuleCodes,
                moduleSearchQuery = current.moduleSearchQuery,
            ).copy(
                successMessage = "Массовая синхронизация запущена.",
                actionInProgress = null,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось запустить массовый импорт.",
            )
        }

    suspend fun syncOne(current: ModuleSyncPageState): ModuleSyncPageState {
        val moduleCode = current.selectedModuleCodes.singleOrNull()?.trim().orEmpty()
        if (moduleCode.isBlank()) {
            return current.copy(
                actionInProgress = null,
                errorMessage = "Выбери один модуль для точечной синхронизации.",
            )
        }
        return runCatching {
            val result = api.syncOne(moduleCode)
            load(
                historyLimit = current.historyLimit,
                preferredRunId = result.syncRunId,
                selectiveSyncVisible = false,
                selectedModuleCodes = setOf(moduleCode),
                moduleSearchQuery = current.moduleSearchQuery,
            ).copy(
                successMessage = "Синхронизация модуля '$moduleCode' запущена.",
                actionInProgress = null,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось синхронизировать модуль.",
            )
        }
    }

    suspend fun syncSelected(current: ModuleSyncPageState): ModuleSyncPageState {
        val moduleCodes = current.selectedModuleCodes
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (moduleCodes.isEmpty()) {
            return current.copy(
                actionInProgress = null,
                errorMessage = "Отметь хотя бы один модуль для синхронизации.",
            )
        }
        if (moduleCodes.size == 1) {
            return syncOne(current.copy(selectedModuleCodes = moduleCodes.toSet()))
        }
        return runCatching {
            val result = api.syncSelected(moduleCodes)
            load(
                historyLimit = current.historyLimit,
                preferredRunId = result.syncRunId,
                selectiveSyncVisible = false,
                selectedModuleCodes = moduleCodes.toSet(),
                moduleSearchQuery = current.moduleSearchQuery,
            ).copy(
                successMessage = "Выборочная синхронизация запущена для ${moduleCodes.size} модулей.",
                actionInProgress = null,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось запустить выборочную синхронизацию.",
            )
        }
    }

    fun beginAction(
        current: ModuleSyncPageState,
        actionName: String,
    ): ModuleSyncPageState =
        current.copy(actionInProgress = actionName, errorMessage = null, successMessage = null)

    private fun resolveSelectedRunId(
        runs: List<ModuleSyncRunSummaryResponse>,
        syncState: ModuleSyncStateResponse,
        preferredRunId: String?,
    ): String? =
        when {
            preferredRunId != null && runs.any { it.syncRunId == preferredRunId } -> preferredRunId
            syncState.activeFullSync != null && runs.any { it.syncRunId == syncState.activeFullSync.syncRunId } ->
                syncState.activeFullSync.syncRunId
            syncState.activeSingleSyncs.isNotEmpty() && runs.any { it.syncRunId == syncState.activeSingleSyncs.first().syncRunId } ->
                syncState.activeSingleSyncs.first().syncRunId
            else -> runs.firstOrNull()?.syncRunId
        }
}
