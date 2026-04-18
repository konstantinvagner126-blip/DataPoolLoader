package com.sbrf.lt.platform.composeui.module_sync

import com.sbrf.lt.platform.composeui.model.ModuleStoreMode

class ModuleSyncStore(
    private val api: ModuleSyncApi,
) {
    suspend fun load(
        historyLimit: Int = 20,
        preferredRunId: String? = null,
        syncOneModuleCode: String = "",
        syncOneInputVisible: Boolean = false,
    ): ModuleSyncPageState {
        val runtimeContextResult = runCatching { api.loadRuntimeContext() }
        val runtimeContext = runtimeContextResult.getOrNull()
        if (runtimeContext == null) {
            return ModuleSyncPageState(
                loading = false,
                errorMessage = runtimeContextResult.exceptionOrNull()?.message ?: "Не удалось загрузить экран импорта модулей.",
                historyLimit = historyLimit,
                syncOneModuleCode = syncOneModuleCode,
                syncOneInputVisible = syncOneInputVisible,
            )
        }

        val syncStateResult = runCatching { api.loadSyncState() }
        val syncState = syncStateResult.getOrDefault(
            ModuleSyncStateResponse(message = "Не удалось загрузить состояние синхронизации."),
        )
        if (runtimeContext.effectiveMode != ModuleStoreMode.DATABASE) {
            return ModuleSyncPageState(
                loading = false,
                runtimeContext = runtimeContext,
                syncState = syncState,
                historyLimit = historyLimit,
                syncOneModuleCode = syncOneModuleCode,
                syncOneInputVisible = syncOneInputVisible,
                errorMessage = syncStateResult.exceptionOrNull()?.message,
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
            runs = runs,
            selectedRunId = selectedRunId,
            selectedRunDetails = details,
            historyLimit = historyLimit,
            syncOneModuleCode = syncOneModuleCode,
            syncOneInputVisible = syncOneInputVisible,
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

    fun updateSyncOneModuleCode(
        current: ModuleSyncPageState,
        moduleCode: String,
    ): ModuleSyncPageState =
        current.copy(syncOneModuleCode = moduleCode)

    fun toggleSyncOneInput(current: ModuleSyncPageState): ModuleSyncPageState =
        current.copy(
            syncOneInputVisible = !current.syncOneInputVisible,
            errorMessage = null,
            successMessage = null,
        )

    suspend fun refresh(current: ModuleSyncPageState): ModuleSyncPageState =
        load(
            historyLimit = current.historyLimit,
            preferredRunId = current.selectedRunId,
            syncOneModuleCode = current.syncOneModuleCode,
            syncOneInputVisible = current.syncOneInputVisible,
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
                syncOneModuleCode = current.syncOneModuleCode,
                syncOneInputVisible = current.syncOneInputVisible,
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
        val moduleCode = current.syncOneModuleCode.trim()
        if (moduleCode.isBlank()) {
            return current.copy(
                actionInProgress = null,
                errorMessage = "Введите код модуля.",
            )
        }
        return runCatching {
            val result = api.syncOne(moduleCode)
            load(
                historyLimit = current.historyLimit,
                preferredRunId = result.syncRunId,
                syncOneModuleCode = moduleCode,
                syncOneInputVisible = false,
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
