package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.sbrf.lt.platform.composeui.foundation.updates.PollingEffect
import com.sbrf.lt.platform.composeui.foundation.updates.WebSocketEffect
import com.sbrf.lt.platform.composeui.foundation.updates.buildWebSocketUrl
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsPageState
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsRouteState
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunsStore
import kotlinx.browser.window
import kotlinx.coroutines.delay

internal suspend fun refreshModuleEditorRunPanel(
    runsStore: ModuleRunsStore,
    currentRoute: () -> ModuleEditorRouteState,
    currentUiState: () -> ModuleEditorPageUiState,
    setUiState: (ModuleEditorPageUiState) -> Unit,
    moduleId: String,
) {
    if (currentUiState().runPanelRefreshInProgress) {
        return
    }
    setUiState(currentUiState().copy(runPanelRefreshInProgress = true))
    try {
        val routeState = ModuleRunsRouteState(currentRoute().storage, moduleId)
        val currentRunPanelState = currentUiState().runPanelState
        val nextRunPanelState = if (currentRunPanelState.history == null) {
            runsStore.load(route = routeState, historyLimit = currentRunPanelState.historyLimit)
        } else {
            runsStore.reloadHistory(
                current = currentRunPanelState,
                route = routeState,
                preferActiveRun = true,
            )
        }
        setUiState(currentUiState().copy(runPanelState = nextRunPanelState))
    } finally {
        setUiState(currentUiState().copy(runPanelRefreshInProgress = false))
    }
}

internal suspend fun refreshModuleEditorCatalog(
    store: ModuleEditorStore,
    currentRoute: () -> ModuleEditorRouteState,
    currentState: () -> ModuleEditorPageState,
    setState: (ModuleEditorPageState) -> Unit,
) {
    setState(store.refreshCatalog(currentState(), currentRoute()))
}

@Composable
internal fun ModuleEditorPageEffects(
    store: ModuleEditorStore,
    runsStore: ModuleRunsStore,
    currentRoute: () -> ModuleEditorRouteState,
    currentState: () -> ModuleEditorPageState,
    setState: (ModuleEditorPageState) -> Unit,
    currentUiState: () -> ModuleEditorPageUiState,
    setUiState: (ModuleEditorPageUiState) -> Unit,
) {
    LaunchedEffect(currentRoute().storage, currentRoute().moduleId, currentRoute().includeHidden, currentRoute().openCreateDialog) {
        setState(store.startLoading(currentState()))
        val loadedState = store.load(currentRoute())
        setState(
            if (currentRoute().storage == "database" && currentRoute().openCreateDialog) {
                window.history.replaceState(
                    null,
                    "",
                    buildComposeEditorUrl(
                        storage = currentRoute().storage,
                        moduleId = currentRoute().moduleId,
                        includeHidden = currentRoute().includeHidden,
                    ),
                )
                store.openCreateModuleDialog(loadedState)
            } else {
                loadedState
            },
        )
    }

    LaunchedEffect(currentState().successMessage) {
        if (!currentState().successMessage.isNullOrBlank()) {
            delay(5_000)
            setState(store.clearSuccessMessage(currentState()))
        }
    }

    LaunchedEffect(currentRoute().storage, currentState().selectedModuleId) {
        val moduleId = currentState().selectedModuleId
        if (moduleId.isNullOrBlank()) {
            setUiState(currentUiState().copy(runPanelState = ModuleRunsPageState(loading = false, historyLimit = 3)))
        } else {
            val loadingState = runsStore.startLoading(ModuleRunsPageState(loading = true, historyLimit = 3))
            setUiState(currentUiState().copy(runPanelState = loadingState))
            setUiState(
                currentUiState().copy(
                    runPanelState = runsStore.load(
                        route = ModuleRunsRouteState(currentRoute().storage, moduleId),
                        historyLimit = 3,
                    ),
                ),
            )
        }
    }

    val selectedModuleId = currentState().selectedModuleId
    val runPanelState = currentUiState().runPanelState
    val hasRunningRun = runPanelState.history?.runs?.any { it.status.equals("RUNNING", ignoreCase = true) } == true

    PollingEffect(
        enabled = currentRoute().storage == "database" && !selectedModuleId.isNullOrBlank() && !runPanelState.loading && hasRunningRun,
        intervalMs = 3000,
        onTick = {
            val moduleId = currentState().selectedModuleId ?: return@PollingEffect
            refreshModuleEditorRunPanel(runsStore, currentRoute, currentUiState, setUiState, moduleId)
        },
    )

    PollingEffect(
        enabled = currentRoute().storage == "files" && !selectedModuleId.isNullOrBlank() && !runPanelState.loading && hasRunningRun,
        intervalMs = 3000,
        onTick = {
            val moduleId = currentState().selectedModuleId ?: return@PollingEffect
            refreshModuleEditorRunPanel(runsStore, currentRoute, currentUiState, setUiState, moduleId)
            refreshModuleEditorCatalog(store, currentRoute, currentState, setState)
        },
    )

    PollingEffect(
        enabled = currentRoute().storage == "database" && !currentState().loading && currentState().modules.isNotEmpty(),
        intervalMs = 3000,
        onTick = {
            refreshModuleEditorCatalog(store, currentRoute, currentState, setState)
        },
    )

    WebSocketEffect(
        enabled = currentRoute().storage == "files" && !selectedModuleId.isNullOrBlank() && !runPanelState.loading,
        url = buildWebSocketUrl(),
        onMessage = {
            val moduleId = currentState().selectedModuleId ?: return@WebSocketEffect
            refreshModuleEditorRunPanel(runsStore, currentRoute, currentUiState, setUiState, moduleId)
            refreshModuleEditorCatalog(store, currentRoute, currentState, setState)
        },
    )
}
