package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreDraftSupport(
    private val statusSupport: ModuleEditorStoreDraftStatusSupport,
    private val fieldSupport: ModuleEditorStoreDraftFieldSupport,
    private val createModuleSupport: ModuleEditorStoreCreateModuleDraftSupport,
) {
    constructor() : this(
        statusSupport = ModuleEditorStoreDraftStatusSupport(),
        fieldSupport = ModuleEditorStoreDraftFieldSupport(),
        createModuleSupport = ModuleEditorStoreCreateModuleDraftSupport(),
    )

    fun clearSuccessMessage(current: ModuleEditorPageState): ModuleEditorPageState =
        statusSupport.clearSuccessMessage(current)

    fun clearErrorMessage(current: ModuleEditorPageState): ModuleEditorPageState =
        statusSupport.clearErrorMessage(current)

    fun selectTab(
        current: ModuleEditorPageState,
        tab: ModuleEditorTab,
    ): ModuleEditorPageState =
        fieldSupport.selectTab(current, tab)

    fun selectSqlResource(
        current: ModuleEditorPageState,
        path: String,
    ): ModuleEditorPageState =
        fieldSupport.selectSqlResource(current, path)

    fun updateConfigText(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        fieldSupport.updateConfigText(current, value)

    fun updateSqlText(
        current: ModuleEditorPageState,
        path: String,
        value: String,
    ): ModuleEditorPageState =
        fieldSupport.updateSqlText(current, path, value)

    fun updateMetadataTitle(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        fieldSupport.updateMetadataTitle(current, value)

    fun updateMetadataDescription(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        fieldSupport.updateMetadataDescription(current, value)

    fun updateMetadataTags(
        current: ModuleEditorPageState,
        value: List<String>,
    ): ModuleEditorPageState =
        fieldSupport.updateMetadataTags(current, value)

    fun updateMetadataHiddenFromUi(
        current: ModuleEditorPageState,
        value: Boolean,
    ): ModuleEditorPageState =
        fieldSupport.updateMetadataHiddenFromUi(current, value)

    fun openCreateModuleDialog(current: ModuleEditorPageState): ModuleEditorPageState =
        createModuleSupport.openCreateModuleDialog(current)

    fun closeCreateModuleDialog(current: ModuleEditorPageState): ModuleEditorPageState =
        createModuleSupport.closeCreateModuleDialog(current)

    fun updateCreateModuleCode(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        createModuleSupport.updateCreateModuleCode(current, value)

    fun updateCreateModuleTitle(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        createModuleSupport.updateCreateModuleTitle(current, value)

    fun updateCreateModuleDescription(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        createModuleSupport.updateCreateModuleDescription(current, value)

    fun updateCreateModuleTagsText(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        createModuleSupport.updateCreateModuleTagsText(current, value)

    fun updateCreateModuleHiddenFromUi(
        current: ModuleEditorPageState,
        value: Boolean,
    ): ModuleEditorPageState =
        createModuleSupport.updateCreateModuleHiddenFromUi(current, value)

    fun updateCreateModuleConfigText(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        createModuleSupport.updateCreateModuleConfigText(current, value)

    fun restoreCreateModuleTemplate(current: ModuleEditorPageState): ModuleEditorPageState =
        createModuleSupport.restoreCreateModuleTemplate(current)

    fun updateConfigFormLocally(
        current: ModuleEditorPageState,
        formState: ConfigFormStateDto,
    ): ModuleEditorPageState =
        fieldSupport.updateConfigFormLocally(current, formState)

    fun startLoading(current: ModuleEditorPageState): ModuleEditorPageState =
        statusSupport.startLoading(current)

    fun beginAction(
        current: ModuleEditorPageState,
        actionName: String,
    ): ModuleEditorPageState =
        statusSupport.beginAction(current, actionName)
}
