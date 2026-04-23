package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun SqlConsoleObjectsPageEffects(
    store: SqlConsoleObjectsStore,
    initialQuery: String,
    initialSource: String,
    navigationTarget: SqlObjectNavigationTarget?,
    currentState: () -> SqlConsoleObjectsPageState,
    setState: (SqlConsoleObjectsPageState) -> Unit,
) {
    LaunchedEffect(store, initialQuery, initialSource) {
        setState(store.startLoading(currentState()))
        var nextState = store.load()
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
            setState(store.beginAction(currentState(), "search"))
            setState(store.search(currentState()))
        }
    }

    LaunchedEffect(store, navigationTarget, currentState().searchResponse) {
        val selection = findSelectedObject(currentState().searchResponse, navigationTarget)
        if (selection == null) {
            val state = currentState()
            if (state.inspectorLoading || state.inspectorErrorMessage != null || state.inspectorResponse != null) {
                setState(store.clearInspector(state))
            }
            return@LaunchedEffect
        }
        if (inspectorMatchesSelection(currentState().inspectorResponse, selection)) {
            return@LaunchedEffect
        }
        setState(store.beginInspectorLoad(currentState()))
        setState(store.loadInspector(currentState(), selection.sourceName, selection.dbObject))
    }
}
