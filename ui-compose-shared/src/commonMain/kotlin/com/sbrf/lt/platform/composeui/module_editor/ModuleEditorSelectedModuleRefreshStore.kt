package com.sbrf.lt.platform.composeui.module_editor

internal interface ModuleEditorSelectedModuleRefreshStore {
    suspend fun refreshSelectedModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
        successMessage: String,
    ): ModuleEditorPageState
}
