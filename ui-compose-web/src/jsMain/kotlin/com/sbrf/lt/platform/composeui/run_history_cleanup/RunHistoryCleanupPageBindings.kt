package com.sbrf.lt.platform.composeui.run_history_cleanup

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class RunHistoryCleanupPageCallbacks(
    val onToggleCleanupSafeguard: (Boolean) -> Unit,
    val onRefreshCleanupPreview: () -> Unit,
    val onExecuteCleanup: () -> Unit,
    val onToggleOutputSafeguard: (Boolean) -> Unit,
    val onRefreshOutputPreview: () -> Unit,
    val onExecuteOutputCleanup: () -> Unit,
)

internal fun runHistoryCleanupPageCallbacks(
    store: RunHistoryCleanupStore,
    scope: CoroutineScope,
    currentState: () -> RunHistoryCleanupPageState,
    setState: (RunHistoryCleanupPageState) -> Unit,
): RunHistoryCleanupPageCallbacks =
    RunHistoryCleanupPageCallbacks(
        onToggleCleanupSafeguard = { disableSafeguard ->
            scope.launch {
                setState(store.updateCleanupSafeguard(currentState(), disableSafeguard))
                setState(store.beginAction(currentState(), "cleanup-preview"))
                setState(store.refreshPreview(currentState()))
            }
        },
        onRefreshCleanupPreview = {
            scope.launch {
                setState(store.beginAction(currentState(), "cleanup-preview"))
                setState(store.refreshPreview(currentState()))
            }
        },
        onExecuteCleanup = {
            if (!window.confirm("Очистить историю запусков по текущему preview?")) {
                return@RunHistoryCleanupPageCallbacks
            }
            scope.launch {
                setState(store.beginAction(currentState(), "cleanup-execute"))
                setState(store.cleanupRunHistory(currentState()))
            }
        },
        onToggleOutputSafeguard = { disableSafeguard ->
            scope.launch {
                setState(store.updateOutputSafeguard(currentState(), disableSafeguard))
                setState(store.beginAction(currentState(), "output-preview"))
                setState(store.refreshOutputPreview(currentState()))
            }
        },
        onRefreshOutputPreview = {
            scope.launch {
                setState(store.beginAction(currentState(), "output-preview"))
                setState(store.refreshOutputPreview(currentState()))
            }
        },
        onExecuteOutputCleanup = {
            if (!window.confirm("Очистить output-каталоги по текущему preview?")) {
                return@RunHistoryCleanupPageCallbacks
            }
            scope.launch {
                setState(store.beginAction(currentState(), "output-execute"))
                setState(store.cleanupOutputs(currentState()))
            }
        },
    )
