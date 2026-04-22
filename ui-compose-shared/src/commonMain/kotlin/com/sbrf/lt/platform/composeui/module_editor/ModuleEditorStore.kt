package com.sbrf.lt.platform.composeui.module_editor


class ModuleEditorStore(
    private val api: ModuleEditorApi,
    private val syncRoute: (storage: String, moduleId: String?, includeHidden: Boolean) -> Unit = { _, _, _ -> },
) {
    private val draftSupport = ModuleEditorStoreDraftSupport()
    private val loadingSupport = ModuleEditorStoreLoadingSupport(api, syncRoute)
    private val actionSupport = ModuleEditorStoreActionSupport(api, loadingSupport)
    private val configFormSupport = ModuleEditorStoreConfigFormSupport(api)
    private val sqlResourceSupport = ModuleEditorStoreSqlResourceSupport(configFormSupport)

    fun clearSuccessMessage(current: ModuleEditorPageState): ModuleEditorPageState =
        draftSupport.clearSuccessMessage(current)

    fun clearErrorMessage(current: ModuleEditorPageState): ModuleEditorPageState =
        draftSupport.clearErrorMessage(current)

    suspend fun load(route: ModuleEditorRouteState): ModuleEditorPageState =
        loadingSupport.load(route)

    suspend fun selectModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
        moduleId: String,
    ): ModuleEditorPageState =
        loadingSupport.selectModule(current, route, moduleId)

    suspend fun refreshCatalog(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        loadingSupport.refreshCatalog(current, route)

    fun selectTab(
        current: ModuleEditorPageState,
        tab: ModuleEditorTab,
    ): ModuleEditorPageState =
        draftSupport.selectTab(current, tab)

    fun selectSqlResource(
        current: ModuleEditorPageState,
        path: String,
    ): ModuleEditorPageState =
        draftSupport.selectSqlResource(current, path)

    fun updateConfigText(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        draftSupport.updateConfigText(current, value)

    fun updateSqlText(
        current: ModuleEditorPageState,
        path: String,
        value: String,
    ): ModuleEditorPageState {
        return draftSupport.updateSqlText(current, path, value)
    }

    fun createSqlResource(
        current: ModuleEditorPageState,
        rawName: String,
    ): ModuleEditorPageState {
        return sqlResourceSupport.createSqlResource(current, rawName)
    }

    suspend fun renameSqlResource(
        current: ModuleEditorPageState,
        rawName: String,
    ): ModuleEditorPageState {
        return sqlResourceSupport.renameSqlResource(current, rawName)
    }

    fun deleteSqlResource(current: ModuleEditorPageState): ModuleEditorPageState {
        return sqlResourceSupport.deleteSqlResource(current)
    }

    fun updateMetadataTitle(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        draftSupport.updateMetadataTitle(current, value)

    fun updateMetadataDescription(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        draftSupport.updateMetadataDescription(current, value)

    fun updateMetadataTags(
        current: ModuleEditorPageState,
        value: List<String>,
    ): ModuleEditorPageState =
        draftSupport.updateMetadataTags(current, value)

    fun updateMetadataHiddenFromUi(
        current: ModuleEditorPageState,
        value: Boolean,
    ): ModuleEditorPageState =
        draftSupport.updateMetadataHiddenFromUi(current, value)

    fun openCreateModuleDialog(current: ModuleEditorPageState): ModuleEditorPageState =
        draftSupport.openCreateModuleDialog(current)

    fun closeCreateModuleDialog(current: ModuleEditorPageState): ModuleEditorPageState =
        draftSupport.closeCreateModuleDialog(current)

    fun updateCreateModuleCode(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        draftSupport.updateCreateModuleCode(current, value)

    fun updateCreateModuleTitle(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        draftSupport.updateCreateModuleTitle(current, value)

    fun updateCreateModuleDescription(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        draftSupport.updateCreateModuleDescription(current, value)

    fun updateCreateModuleTagsText(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        draftSupport.updateCreateModuleTagsText(current, value)

    fun updateCreateModuleHiddenFromUi(
        current: ModuleEditorPageState,
        value: Boolean,
    ): ModuleEditorPageState =
        draftSupport.updateCreateModuleHiddenFromUi(current, value)

    fun updateCreateModuleConfigText(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        draftSupport.updateCreateModuleConfigText(current, value)

    fun restoreCreateModuleTemplate(current: ModuleEditorPageState): ModuleEditorPageState =
        draftSupport.restoreCreateModuleTemplate(current)

    fun startLoading(current: ModuleEditorPageState): ModuleEditorPageState =
        draftSupport.startLoading(current)

    fun beginAction(
        current: ModuleEditorPageState,
        actionName: String,
    ): ModuleEditorPageState =
        draftSupport.beginAction(current, actionName)

    suspend fun saveFilesModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.saveFilesModule(current, route)

    suspend fun saveDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.saveDatabaseWorkingCopy(current, route)

    suspend fun discardDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.discardDatabaseWorkingCopy(current, route)

    suspend fun publishDatabaseWorkingCopy(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.publishDatabaseWorkingCopy(current, route)

    suspend fun runFilesModule(current: ModuleEditorPageState): ModuleEditorPageState =
        actionSupport.runFilesModule(current)

    suspend fun runDatabaseModule(current: ModuleEditorPageState): ModuleEditorPageState =
        actionSupport.runDatabaseModule(current)

    suspend fun createDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.createDatabaseModule(current, route)

    suspend fun deleteDatabaseModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        actionSupport.deleteDatabaseModule(current, route)

    fun updateConfigFormLocally(
        current: ModuleEditorPageState,
        formState: ConfigFormStateDto,
    ): ModuleEditorPageState =
        draftSupport.updateConfigFormLocally(current, formState)

    suspend fun syncConfigFormFromConfigDraft(current: ModuleEditorPageState): ModuleEditorPageState {
        return configFormSupport.syncConfigFormFromConfigDraft(current)
    }

    suspend fun applyConfigForm(
        current: ModuleEditorPageState,
        formState: ConfigFormStateDto,
    ): ModuleEditorPageState =
        configFormSupport.applyConfigForm(current, formState)

    fun startConfigFormSync(current: ModuleEditorPageState): ModuleEditorPageState =
        configFormSupport.startConfigFormSync(current)
}
