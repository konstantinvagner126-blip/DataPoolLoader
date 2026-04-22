package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorWorkflowStoreSupport(
    private val actionSupport: ModuleEditorStoreActionSupport,
    private val configFormSupport: ModuleEditorStoreConfigFormSupport,
) : ModuleEditorWorkflowStore {
    override suspend fun saveFilesModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.saveFilesModule(current, route)

    override suspend fun saveDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.saveDatabaseWorkingCopy(current, route)

    override suspend fun discardDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.discardDatabaseWorkingCopy(current, route)

    override suspend fun publishDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.publishDatabaseWorkingCopy(current, route)

    override suspend fun runFilesModule(current: ModuleEditorPageState): ModuleEditorPageState =
        actionSupport.runFilesModule(current)

    override suspend fun runDatabaseModule(current: ModuleEditorPageState): ModuleEditorPageState =
        actionSupport.runDatabaseModule(current)

    override suspend fun createDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.createDatabaseModule(current, route)

    override suspend fun deleteDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.deleteDatabaseModule(current, route)

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
