package com.sbrf.lt.platform.composeui.module_sync

internal class ModuleSyncStoreActionSupport(
    private val api: ModuleSyncApi,
    loadStore: ModuleSyncLoadStore,
) {
    private val selectionSupport = ModuleSyncStoreActionSelectionSupport()
    private val stateSupport = ModuleSyncStoreActionStateSupport()
    private val reloadSupport = ModuleSyncStoreActionReloadSupport(loadStore, stateSupport)

    suspend fun syncAll(current: ModuleSyncPageState): ModuleSyncPageState =
        runCatching {
            val result = api.syncAll()
            reloadSupport.reloadAfterFullSync(current, result.syncRunId)
        }.getOrElse { error ->
            stateSupport.applyFailure(current, error, "Не удалось запустить массовый импорт.")
        }

    suspend fun syncOne(current: ModuleSyncPageState): ModuleSyncPageState {
        val moduleCode = selectionSupport.resolveSingleModuleCode(current.selectedModuleCodes)
        if (moduleCode == null) {
            return stateSupport.applyFailure(
                current,
                IllegalStateException("Выбери один модуль для точечной синхронизации."),
                "Выбери один модуль для точечной синхронизации.",
            )
        }
        return runCatching {
            val result = api.syncOne(moduleCode)
            reloadSupport.reloadAfterSingleSync(current, result.syncRunId, moduleCode)
        }.getOrElse { error ->
            stateSupport.applyFailure(current, error, "Не удалось синхронизировать модуль.")
        }
    }

    suspend fun syncSelected(current: ModuleSyncPageState): ModuleSyncPageState {
        val moduleCodes = selectionSupport.resolveSelectedModuleCodes(current.selectedModuleCodes)
        if (moduleCodes.isEmpty()) {
            return stateSupport.applyFailure(
                current,
                IllegalStateException("Отметь хотя бы один модуль для синхронизации."),
                "Отметь хотя бы один модуль для синхронизации.",
            )
        }
        if (moduleCodes.size == 1) {
            return syncOne(current.copy(selectedModuleCodes = moduleCodes.toSet()))
        }
        return runCatching {
            val result = api.syncSelected(moduleCodes)
            reloadSupport.reloadAfterSelectedSync(current, result.syncRunId, moduleCodes)
        }.getOrElse { error ->
            stateSupport.applyFailure(current, error, "Не удалось запустить выборочную синхронизацию.")
        }
    }
}
