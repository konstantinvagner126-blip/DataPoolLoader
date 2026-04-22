package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun SqlConsoleObjectsPageEffects(
    store: SqlConsoleObjectsStore,
    initialQuery: String,
    initialSource: String,
    currentState: () -> SqlConsoleObjectsPageState,
    setState: (SqlConsoleObjectsPageState) -> Unit,
) {
    LaunchedEffect(store, initialQuery, initialSource) {
        setState(store.startLoading(currentState()))
        var nextState = store.load()
        if (initialQuery.isNotBlank()) {
            nextState = store.updateQuery(nextState, initialQuery)
        }
        if (initialSource.isNotBlank() && initialSource in (nextState.info?.sourceNames ?: emptyList())) {
            nextState = nextState.copy(selectedSourceNames = listOf(initialSource))
        }
        setState(nextState)
        if (initialQuery.length >= 2) {
            setState(store.beginAction(currentState(), "search"))
            setState(store.search(currentState()))
        }
    }
}
