package com.sbrf.lt.platform.composeui.module_editor

internal interface ModuleEditorDraftStore {
    fun clearSuccessMessage(current: ModuleEditorPageState): ModuleEditorPageState
    fun clearErrorMessage(current: ModuleEditorPageState): ModuleEditorPageState
    fun selectTab(current: ModuleEditorPageState, tab: ModuleEditorTab): ModuleEditorPageState
    fun selectSqlResource(current: ModuleEditorPageState, path: String): ModuleEditorPageState
    fun updateConfigText(current: ModuleEditorPageState, value: String): ModuleEditorPageState
    fun updateSqlText(current: ModuleEditorPageState, path: String, value: String): ModuleEditorPageState
    fun createSqlResource(current: ModuleEditorPageState, rawName: String): ModuleEditorPageState
    suspend fun renameSqlResource(current: ModuleEditorPageState, rawName: String): ModuleEditorPageState
    fun deleteSqlResource(current: ModuleEditorPageState): ModuleEditorPageState
    fun updateMetadataTitle(current: ModuleEditorPageState, value: String): ModuleEditorPageState
    fun updateMetadataDescription(current: ModuleEditorPageState, value: String): ModuleEditorPageState
    fun updateMetadataTags(current: ModuleEditorPageState, value: List<String>): ModuleEditorPageState
    fun updateMetadataHiddenFromUi(current: ModuleEditorPageState, value: Boolean): ModuleEditorPageState
    fun openCreateModuleDialog(current: ModuleEditorPageState): ModuleEditorPageState
    fun closeCreateModuleDialog(current: ModuleEditorPageState): ModuleEditorPageState
    fun updateCreateModuleCode(current: ModuleEditorPageState, value: String): ModuleEditorPageState
    fun updateCreateModuleTitle(current: ModuleEditorPageState, value: String): ModuleEditorPageState
    fun updateCreateModuleDescription(current: ModuleEditorPageState, value: String): ModuleEditorPageState
    fun updateCreateModuleTagsText(current: ModuleEditorPageState, value: String): ModuleEditorPageState
    fun updateCreateModuleHiddenFromUi(current: ModuleEditorPageState, value: Boolean): ModuleEditorPageState
    fun updateCreateModuleConfigText(current: ModuleEditorPageState, value: String): ModuleEditorPageState
    fun restoreCreateModuleTemplate(current: ModuleEditorPageState): ModuleEditorPageState
    fun updateConfigFormLocally(current: ModuleEditorPageState, formState: ConfigFormStateDto): ModuleEditorPageState
    fun startLoading(current: ModuleEditorPageState): ModuleEditorPageState
    fun beginAction(current: ModuleEditorPageState, actionName: String): ModuleEditorPageState
}
