package com.sbrf.lt.platform.composeui.module_sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.sbrf.lt.platform.composeui.foundation.updates.PollingEffect
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode

@Composable
internal fun ModuleSyncPageEffects(
    store: ModuleSyncStore,
    currentState: () -> ModuleSyncPageState,
    setState: (ModuleSyncPageState) -> Unit,
) {
    LaunchedEffect(store) {
        setState(store.startLoading(currentState()))
        setState(store.load())
    }

    val runtimeContext = currentState().runtimeContext
    val syncState = currentState().syncState
    val databaseModeActive = runtimeContext?.effectiveMode == ModuleStoreMode.DATABASE
    val hasActiveSync = syncState?.maintenanceMode == true || syncState?.activeSingleSyncs?.isNotEmpty() == true

    PollingEffect(
        enabled = !currentState().loading && (databaseModeActive || hasActiveSync),
        intervalMs = 5000,
        onTick = {
            setState(store.refresh(currentState()))
        },
    )
}
