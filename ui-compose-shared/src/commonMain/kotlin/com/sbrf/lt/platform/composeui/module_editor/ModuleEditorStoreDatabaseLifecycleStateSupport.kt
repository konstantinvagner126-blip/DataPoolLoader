package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreDatabaseLifecycleStateSupport {
    fun nextRouteAfterCreate(
        route: ModuleEditorRouteState,
        draft: CreateModuleDraft,
        response: CreateDbModuleResponseDto,
    ): ModuleEditorRouteState =
        route.copy(
            moduleId = response.moduleCode,
            includeHidden = route.includeHidden || draft.hiddenFromUi,
        )

    fun nextRouteAfterDelete(route: ModuleEditorRouteState): ModuleEditorRouteState =
        route.copy(moduleId = null)

    fun applyCreatedModuleLoadedState(
        loaded: ModuleEditorPageState,
        response: CreateDbModuleResponseDto,
    ): ModuleEditorPageState =
        loaded.copy(
            loading = false,
            actionInProgress = null,
            errorMessage = null,
            successMessage = response.message,
            activeTab = ModuleEditorTab.SETTINGS,
            createModuleDialogOpen = false,
            createModuleDraft = CreateModuleDraft(),
        )

    fun applyDeletedModuleLoadedState(
        loaded: ModuleEditorPageState,
        response: DeleteModuleResponseDto,
    ): ModuleEditorPageState =
        loaded.copy(
            loading = false,
            errorMessage = null,
            successMessage = response.message,
            actionInProgress = null,
            createModuleDialogOpen = false,
            createModuleDraft = CreateModuleDraft(),
        )
}
