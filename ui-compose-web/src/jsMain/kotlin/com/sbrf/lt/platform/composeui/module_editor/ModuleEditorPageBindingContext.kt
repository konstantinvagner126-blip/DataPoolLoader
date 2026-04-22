package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import kotlinx.coroutines.CoroutineScope

internal class ModuleEditorPageBindingContext(
    val store: ModuleEditorStore,
    val credentialsHttpClient: ComposeHttpClient,
    val scope: CoroutineScope,
    private val currentRouteProvider: () -> ModuleEditorRouteState,
    private val setCurrentRouteProvider: (ModuleEditorRouteState) -> Unit,
    private val currentStateProvider: () -> ModuleEditorPageState,
    private val setStateProvider: (ModuleEditorPageState) -> Unit,
    private val currentUiStateProvider: () -> ModuleEditorPageUiState,
    private val setUiStateProvider: (ModuleEditorPageUiState) -> Unit,
    val refreshModuleCatalog: suspend () -> Unit,
    val refreshEditorRunPanel: suspend (String) -> Unit,
) {
    fun currentRoute(): ModuleEditorRouteState = currentRouteProvider()
    fun setCurrentRoute(route: ModuleEditorRouteState) = setCurrentRouteProvider(route)
    fun currentState(): ModuleEditorPageState = currentStateProvider()
    fun setState(state: ModuleEditorPageState) = setStateProvider(state)
    fun currentUiState(): ModuleEditorPageUiState = currentUiStateProvider()

    fun updateState(transform: (ModuleEditorPageState) -> ModuleEditorPageState) {
        setState(transform(currentState()))
    }

    fun updateUiState(transform: (ModuleEditorPageUiState) -> ModuleEditorPageUiState) {
        setUiStateProvider(transform(currentUiState()))
    }
}
