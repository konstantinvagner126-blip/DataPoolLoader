package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorDraftStoreSupport(
    private val draftSupport: ModuleEditorStoreDraftSupport,
    private val sqlResourceSupport: ModuleEditorStoreSqlResourceSupport,
) : ModuleEditorDraftStore {
    override fun clearSuccessMessage(current: ModuleEditorPageState): ModuleEditorPageState =
        draftSupport.clearSuccessMessage(current)

    override fun clearErrorMessage(current: ModuleEditorPageState): ModuleEditorPageState =
        draftSupport.clearErrorMessage(current)

    override fun selectTab(current: ModuleEditorPageState, tab: ModuleEditorTab): ModuleEditorPageState =
        draftSupport.selectTab(current, tab)

    override fun selectSqlResource(current: ModuleEditorPageState, path: String): ModuleEditorPageState =
        draftSupport.selectSqlResource(current, path)

    override fun updateConfigText(current: ModuleEditorPageState, value: String): ModuleEditorPageState =
        draftSupport.updateConfigText(current, value)

    override fun updateSqlText(
        current: ModuleEditorPageState,
        path: String,
        value: String,
    ): ModuleEditorPageState =
        draftSupport.updateSqlText(current, path, value)

    override fun createSqlResource(current: ModuleEditorPageState, rawName: String): ModuleEditorPageState =
        sqlResourceSupport.createSqlResource(current, rawName)

    override suspend fun renameSqlResource(
        current: ModuleEditorPageState,
        rawName: String,
    ): ModuleEditorPageState =
        sqlResourceSupport.renameSqlResource(current, rawName)

    override fun deleteSqlResource(current: ModuleEditorPageState): ModuleEditorPageState =
        sqlResourceSupport.deleteSqlResource(current)

    override fun updateMetadataTitle(current: ModuleEditorPageState, value: String): ModuleEditorPageState =
        draftSupport.updateMetadataTitle(current, value)

    override fun updateMetadataDescription(current: ModuleEditorPageState, value: String): ModuleEditorPageState =
        draftSupport.updateMetadataDescription(current, value)

    override fun updateMetadataTags(current: ModuleEditorPageState, value: List<String>): ModuleEditorPageState =
        draftSupport.updateMetadataTags(current, value)

    override fun updateMetadataHiddenFromUi(current: ModuleEditorPageState, value: Boolean): ModuleEditorPageState =
        draftSupport.updateMetadataHiddenFromUi(current, value)

    override fun openCreateModuleDialog(current: ModuleEditorPageState): ModuleEditorPageState =
        draftSupport.openCreateModuleDialog(current)

    override fun closeCreateModuleDialog(current: ModuleEditorPageState): ModuleEditorPageState =
        draftSupport.closeCreateModuleDialog(current)

    override fun updateCreateModuleCode(current: ModuleEditorPageState, value: String): ModuleEditorPageState =
        draftSupport.updateCreateModuleCode(current, value)

    override fun updateCreateModuleTitle(current: ModuleEditorPageState, value: String): ModuleEditorPageState =
        draftSupport.updateCreateModuleTitle(current, value)

    override fun updateCreateModuleDescription(current: ModuleEditorPageState, value: String): ModuleEditorPageState =
        draftSupport.updateCreateModuleDescription(current, value)

    override fun updateCreateModuleTagsText(current: ModuleEditorPageState, value: String): ModuleEditorPageState =
        draftSupport.updateCreateModuleTagsText(current, value)

    override fun updateCreateModuleHiddenFromUi(current: ModuleEditorPageState, value: Boolean): ModuleEditorPageState =
        draftSupport.updateCreateModuleHiddenFromUi(current, value)

    override fun updateCreateModuleConfigText(current: ModuleEditorPageState, value: String): ModuleEditorPageState =
        draftSupport.updateCreateModuleConfigText(current, value)

    override fun restoreCreateModuleTemplate(current: ModuleEditorPageState): ModuleEditorPageState =
        draftSupport.restoreCreateModuleTemplate(current)

    override fun updateConfigFormLocally(
        current: ModuleEditorPageState,
        formState: ConfigFormStateDto,
    ): ModuleEditorPageState =
        draftSupport.updateConfigFormLocally(current, formState)

    override fun startLoading(current: ModuleEditorPageState): ModuleEditorPageState =
        draftSupport.startLoading(current)

    override fun beginAction(current: ModuleEditorPageState, actionName: String): ModuleEditorPageState =
        draftSupport.beginAction(current, actionName)
}
