package com.sbrf.lt.platform.composeui.run_history_cleanup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun RunHistoryCleanupPageEffects(
    store: RunHistoryCleanupStore,
    currentState: () -> RunHistoryCleanupPageState,
    setState: (RunHistoryCleanupPageState) -> Unit,
) {
    LaunchedEffect(store) {
        setState(store.startLoading(currentState()))
        setState(store.load())
    }
}
