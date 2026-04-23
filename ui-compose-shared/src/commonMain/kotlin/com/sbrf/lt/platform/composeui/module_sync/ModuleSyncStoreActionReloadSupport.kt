package com.sbrf.lt.platform.composeui.module_sync

internal class ModuleSyncStoreActionReloadSupport(
    private val loadStore: ModuleSyncLoadStore,
    private val stateSupport: ModuleSyncStoreActionStateSupport = ModuleSyncStoreActionStateSupport(),
) {
    suspend fun reloadAfterFullSync(
        current: ModuleSyncPageState,
        syncRunId: String,
    ): ModuleSyncPageState =
        stateSupport.applySuccess(
            reloaded = loadStore.load(
                historyLimit = current.historyLimit,
                preferredRunId = syncRunId,
                selectiveSyncVisible = current.selectiveSyncVisible,
                selectedModuleCodes = current.selectedModuleCodes,
                moduleSearchQuery = current.moduleSearchQuery,
            ),
            successMessage = "Массовая синхронизация запущена.",
        )

    suspend fun reloadAfterSingleSync(
        current: ModuleSyncPageState,
        syncRunId: String,
        moduleCode: String,
    ): ModuleSyncPageState =
        stateSupport.applySuccess(
            reloaded = loadStore.load(
                historyLimit = current.historyLimit,
                preferredRunId = syncRunId,
                selectiveSyncVisible = false,
                selectedModuleCodes = setOf(moduleCode),
                moduleSearchQuery = current.moduleSearchQuery,
            ),
            successMessage = "Синхронизация модуля '$moduleCode' запущена.",
        )

    suspend fun reloadAfterSelectedSync(
        current: ModuleSyncPageState,
        syncRunId: String,
        moduleCodes: List<String>,
    ): ModuleSyncPageState =
        stateSupport.applySuccess(
            reloaded = loadStore.load(
                historyLimit = current.historyLimit,
                preferredRunId = syncRunId,
                selectiveSyncVisible = false,
                selectedModuleCodes = moduleCodes.toSet(),
                moduleSearchQuery = current.moduleSearchQuery,
            ),
            successMessage = "Выборочная синхронизация запущена для ${moduleCodes.size} модулей.",
        )
}
