package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreActionSupport(
    private val api: ModuleEditorApi,
    private val loadingSupport: ModuleEditorStoreLoadingSupport,
) {
    private val saveActionSupport = ModuleEditorStoreSaveActionSupport(api, loadingSupport)
    private val runActionSupport = ModuleEditorStoreRunActionSupport(api)
    private val databaseLifecycleActionSupport = ModuleEditorStoreDatabaseLifecycleActionSupport(api, loadingSupport)

    suspend fun saveFilesModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        saveActionSupport.saveFilesModule(current, route)

    suspend fun saveDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        saveActionSupport.saveDatabaseWorkingCopy(current, route)

    suspend fun discardDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        saveActionSupport.discardDatabaseWorkingCopy(current, route)

    suspend fun publishDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        saveActionSupport.publishDatabaseWorkingCopy(current, route)

    suspend fun runFilesModule(current: ModuleEditorPageState): ModuleEditorPageState =
        runActionSupport.runFilesModule(current)

    suspend fun runDatabaseModule(current: ModuleEditorPageState): ModuleEditorPageState =
        runActionSupport.runDatabaseModule(current)

    suspend fun createDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        databaseLifecycleActionSupport.createDatabaseModule(current, route)

    suspend fun deleteDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        databaseLifecycleActionSupport.deleteDatabaseModule(current, route)
}
