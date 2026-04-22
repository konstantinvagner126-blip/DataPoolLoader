package com.sbrf.lt.platform.composeui.module_runs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class ModuleRunsPageUiState(
    val liveRefreshInProgress: Boolean = false,
    val showTechnicalDiagnostics: Boolean = false,
)

internal data class ModuleRunsPageCallbacks(
    val onHistoryLimitChange: (Int) -> Unit,
    val onHistoryFilterChange: (ModuleRunsHistoryFilter) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onSelectRun: (String) -> Unit,
    val onToggleTechnicalDiagnostics: () -> Unit,
)

internal fun moduleRunsPageCallbacks(
    store: ModuleRunsStore,
    scope: CoroutineScope,
    route: ModuleRunsRouteState,
    currentState: () -> ModuleRunsPageState,
    setState: (ModuleRunsPageState) -> Unit,
    currentUiState: () -> ModuleRunsPageUiState,
    setUiState: (ModuleRunsPageUiState) -> Unit,
): ModuleRunsPageCallbacks {
    fun updateUiState(transform: (ModuleRunsPageUiState) -> ModuleRunsPageUiState) {
        setUiState(transform(currentUiState()))
    }

    suspend fun resolveVisibleRunSelection(nextState: ModuleRunsPageState): ModuleRunsPageState {
        val nextVisibleRuns = filterRuns(nextState.history?.runs.orEmpty(), nextState.historyFilter, nextState.searchQuery)
        return when {
            nextVisibleRuns.isEmpty() -> nextState.copy(
                selectedRunId = null,
                selectedRunDetails = null,
            )

            nextVisibleRuns.any { it.runId == nextState.selectedRunId } -> nextState
            else -> store.selectRun(nextState, route, nextVisibleRuns.first().runId)
        }
    }

    return ModuleRunsPageCallbacks(
        onHistoryLimitChange = { nextLimit ->
            scope.launch {
                setState(store.startLoading(currentState()))
                setState(store.updateHistoryLimit(currentState(), route, nextLimit))
            }
        },
        onHistoryFilterChange = { nextFilter ->
            scope.launch {
                val nextState = store.updateHistoryFilter(currentState(), nextFilter)
                setState(resolveVisibleRunSelection(nextState))
            }
        },
        onSearchQueryChange = { nextQuery ->
            scope.launch {
                val nextState = store.updateSearchQuery(currentState(), nextQuery)
                setState(resolveVisibleRunSelection(nextState))
            }
        },
        onSelectRun = { runId ->
            scope.launch {
                setState(store.startLoading(currentState()))
                setState(store.selectRun(currentState(), route, runId))
            }
        },
        onToggleTechnicalDiagnostics = {
            updateUiState { it.copy(showTechnicalDiagnostics = !it.showTechnicalDiagnostics) }
        },
    )
}
