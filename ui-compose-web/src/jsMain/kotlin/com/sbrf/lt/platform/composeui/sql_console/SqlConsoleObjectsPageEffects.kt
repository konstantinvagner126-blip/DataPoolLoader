package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@Composable
internal fun SqlConsoleObjectsPageEffects(
    store: SqlConsoleObjectsStore,
    workspaceId: String,
    initialQuery: String,
    initialSource: String,
    navigationTarget: SqlObjectNavigationTarget?,
    currentState: () -> SqlConsoleObjectsPageState,
    setState: (SqlConsoleObjectsPageState) -> Unit,
) {
    LaunchedEffect(store, workspaceId, initialQuery, initialSource) {
        setState(store.startLoading(currentState()))
        var nextState = store.load(workspaceId)
        if (initialQuery.isNotBlank()) {
            nextState = store.updateQuery(nextState, initialQuery)
        }
        if (initialSource.isNotBlank() && initialSource in (nextState.info?.sourceCatalogNames() ?: emptyList())) {
            val selectionState = initializeSelectedSourceState(
                groups = nextState.info?.groups.orEmpty(),
                selectedSourceNames = listOf(initialSource),
            )
            nextState = nextState.copy(
                selectedSourceNames = selectionState.selectedSourceNames,
                selectedGroupNames = selectionState.selectedGroupNames,
                manuallyIncludedSourceNames = selectionState.manuallyIncludedSourceNames,
                manuallyExcludedSourceNames = selectionState.manuallyExcludedSourceNames,
            )
        }
        setState(nextState)
        if (initialQuery.length >= 2) {
            val searchState = store.beginAction(nextState, "search")
            setState(searchState)
            setState(store.search(searchState))
        }
    }

    LaunchedEffect(
        store,
        navigationTarget,
        currentState().loading,
        currentState().actionInProgress,
        currentState().query,
        currentState().searchResponse,
    ) {
        val state = currentState()
        if (state.loading || state.info == null) {
            return@LaunchedEffect
        }
        val hasDirectInspectorTarget = directInspectorSelection(navigationTarget) != null
        val waitingForSearchResult = hasDirectInspectorTarget &&
            state.query.trim().length >= 2 &&
            state.searchResponse == null
        if (state.actionInProgress == "search" || waitingForSearchResult) {
            return@LaunchedEffect
        }
        val selection = resolveInspectorSelection(state.searchResponse, navigationTarget)
        if (selection == null) {
            if (state.inspectorLoading || state.inspectorErrorMessage != null || state.inspectorResponse != null) {
                setState(store.clearInspector(state))
            }
            return@LaunchedEffect
        }
        if (state.inspectorLoading || inspectorMatchesSelection(state.inspectorResponse, selection)) {
            return@LaunchedEffect
        }
        val inspectorLoadingState = store.beginInspectorLoad(state)
        setState(inspectorLoadingState)
        setState(store.loadInspector(inspectorLoadingState, selection.sourceName, selection.dbObject))
    }

    LaunchedEffect(
        workspaceId,
        currentState().selectedGroupNames.joinToString("\u0001"),
        currentState().selectedSourceNames.joinToString("\u0001"),
    ) {
        if (currentState().loading || currentState().info == null) {
            return@LaunchedEffect
        }
        delay(300)
        val latestState = currentState()
        if (latestState.loading || latestState.info == null) {
            return@LaunchedEffect
        }
        setState(store.persistState(latestState, workspaceId))
    }
}
