package com.sbrf.lt.platform.composeui.module_sync

internal class ModuleSyncStoreActionSupport(
    private val api: ModuleSyncApi,
    private val loadingSupport: ModuleSyncStoreLoadingSupport,
) {
    suspend fun syncAll(current: ModuleSyncPageState): ModuleSyncPageState =
        runCatching {
            val result = api.syncAll()
            loadingSupport.load(
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
            loadingSupport.load(
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
            loadingSupport.load(
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
}
