package com.sbrf.lt.platform.composeui.module_sync

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class ModuleSyncPageCallbacks(
    val onToggleSelectiveSync: () -> Unit,
    val onSyncAll: () -> Unit,
    val onSyncSelected: () -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onToggleModule: (String) -> Unit,
    val onSelectAll: () -> Unit,
    val onClearSelection: () -> Unit,
    val onHistoryLimitChange: (Int) -> Unit,
    val onSelectRun: (String) -> Unit,
)

internal fun moduleSyncPageCallbacks(
    store: ModuleSyncStore,
    scope: CoroutineScope,
    currentState: () -> ModuleSyncPageState,
    setState: (ModuleSyncPageState) -> Unit,
): ModuleSyncPageCallbacks =
    ModuleSyncPageCallbacks(
        onToggleSelectiveSync = {
            setState(store.toggleSelectiveSync(currentState()))
        },
        onSyncAll = {
            if (!window.confirm("Синхронизировать все файловые модули в базу данных?")) {
                return@ModuleSyncPageCallbacks
            }
            scope.launch {
                setState(store.beginAction(currentState(), "sync-all"))
                setState(store.syncAll(currentState()))
            }
        },
        onSyncSelected = {
            scope.launch {
                setState(store.beginAction(currentState(), "sync-selected"))
                setState(store.syncSelected(currentState()))
            }
        },
        onSearchQueryChange = { query ->
            setState(store.updateModuleSearchQuery(currentState(), query))
        },
        onToggleModule = { moduleCode ->
            setState(store.toggleModuleSelection(currentState(), moduleCode))
        },
        onSelectAll = {
            val current = currentState()
            setState(
                store.selectAllModules(
                    current,
                    filterSelectableModules(current).map { it.id },
                ),
            )
        },
        onClearSelection = {
            setState(store.clearSelectedModules(currentState()))
        },
        onHistoryLimitChange = { limit ->
            scope.launch {
                val nextState = store.updateHistoryLimit(currentState(), limit)
                setState(store.startLoading(nextState))
                setState(
                    store.load(
                        historyLimit = limit,
                        preferredRunId = nextState.selectedRunId,
                        selectiveSyncVisible = nextState.selectiveSyncVisible,
                        selectedModuleCodes = nextState.selectedModuleCodes,
                        moduleSearchQuery = nextState.moduleSearchQuery,
                    ),
                )
            }
        },
        onSelectRun = { syncRunId ->
            scope.launch {
                setState(store.selectRun(currentState(), syncRunId))
            }
        },
    )
