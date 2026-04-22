package com.sbrf.lt.platform.composeui.module_runs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.sbrf.lt.platform.composeui.foundation.updates.PollingEffect
import com.sbrf.lt.platform.composeui.foundation.updates.WebSocketEffect
import com.sbrf.lt.platform.composeui.foundation.updates.buildWebSocketUrl

@Composable
internal fun ModuleRunsPageEffects(
    store: ModuleRunsStore,
    route: ModuleRunsRouteState,
    currentState: () -> ModuleRunsPageState,
    setState: (ModuleRunsPageState) -> Unit,
    currentUiState: () -> ModuleRunsPageUiState,
    setUiState: (ModuleRunsPageUiState) -> Unit,
    hasRunningRun: Boolean,
) {
    suspend fun reloadHistorySafely() {
        if (currentUiState().liveRefreshInProgress) {
            return
        }
        setUiState(currentUiState().copy(liveRefreshInProgress = true))
        try {
            setState(store.reloadHistory(currentState(), route))
        } finally {
            setUiState(currentUiState().copy(liveRefreshInProgress = false))
        }
    }

    LaunchedEffect(route.storage, route.moduleId) {
        setState(store.startLoading(currentState()))
        setState(store.load(route, currentState().historyLimit))
    }

    PollingEffect(
        enabled = !currentState().loading && hasRunningRun,
        intervalMs = 3000,
        onTick = {
            reloadHistorySafely()
        },
    )

    WebSocketEffect(
        enabled = route.storage == "files" && !currentState().loading,
        url = buildWebSocketUrl(),
        onMessage = {
            reloadHistorySafely()
        },
    )
}
