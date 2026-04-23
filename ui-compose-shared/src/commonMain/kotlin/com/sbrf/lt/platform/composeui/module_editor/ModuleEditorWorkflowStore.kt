package com.sbrf.lt.platform.composeui.module_editor

internal interface ModuleEditorWorkflowStore {
    suspend fun saveModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState
    suspend fun discardDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState
    suspend fun publishDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState
    suspend fun runModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState
    suspend fun createDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState
    suspend fun deleteDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState
    suspend fun syncConfigFormFromConfigDraft(current: ModuleEditorPageState): ModuleEditorPageState
    suspend fun applyConfigForm(
        current: ModuleEditorPageState,
        formState: ConfigFormStateDto,
    ): ModuleEditorPageState
    fun startConfigFormSync(current: ModuleEditorPageState): ModuleEditorPageState
}
