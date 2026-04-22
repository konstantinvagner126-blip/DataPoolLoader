package com.sbrf.lt.platform.composeui.module_editor

internal interface ModuleEditorLoadingStore {
    suspend fun load(route: ModuleEditorRouteState): ModuleEditorPageState
    suspend fun selectModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
        moduleId: String,
    ): ModuleEditorPageState
    suspend fun refreshCatalog(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState
}
