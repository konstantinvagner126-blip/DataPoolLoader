package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorWorkflowStoreSupport(
    private val saveActionSupport: ModuleEditorStoreSaveActionSupport,
    private val runActionSupport: ModuleEditorStoreRunActionSupport,
    private val databaseLifecycleActionSupport: ModuleEditorStoreDatabaseLifecycleActionSupport,
    private val configFormSupport: ModuleEditorStoreConfigFormSupport,
) : ModuleEditorWorkflowStore {
    override suspend fun saveModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        saveActionSupport.saveModule(current, route)

    override suspend fun discardDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        saveActionSupport.discardDatabaseWorkingCopy(current, route)

    override suspend fun publishDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        saveActionSupport.publishDatabaseWorkingCopy(current, route)

    override suspend fun runModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        runActionSupport.runModule(current, route)

    override suspend fun createDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        databaseLifecycleActionSupport.createDatabaseModule(current, route)

    override suspend fun deleteDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        databaseLifecycleActionSupport.deleteDatabaseModule(current, route)

    override suspend fun syncConfigFormFromConfigDraft(current: ModuleEditorPageState): ModuleEditorPageState =
        configFormSupport.syncConfigFormFromConfigDraft(current)

    override suspend fun applyConfigForm(
        current: ModuleEditorPageState,
        formState: ConfigFormStateDto,
    ): ModuleEditorPageState =
        configFormSupport.applyConfigForm(current, formState)

    override fun startConfigFormSync(current: ModuleEditorPageState): ModuleEditorPageState =
        configFormSupport.startConfigFormSync(current)
}
