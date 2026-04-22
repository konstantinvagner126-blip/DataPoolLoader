package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreDraftSupport {
    fun clearSuccessMessage(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(successMessage = null)

    fun clearErrorMessage(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(errorMessage = null)

    fun selectTab(
        current: ModuleEditorPageState,
        tab: ModuleEditorTab,
    ): ModuleEditorPageState =
        current.copy(activeTab = tab)

    fun selectSqlResource(
        current: ModuleEditorPageState,
        path: String,
    ): ModuleEditorPageState =
        current.copy(selectedSqlPath = path)

    fun updateConfigText(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        if (current.configTextDraft == value) {
            current
        } else {
            current.copy(configTextDraft = value)
        }

    fun updateSqlText(
        current: ModuleEditorPageState,
        path: String,
        value: String,
    ): ModuleEditorPageState =
        if (current.sqlContentsDraft[path] == value) {
            current
        } else {
            current.copy(
                sqlContentsDraft = current.sqlContentsDraft + (path to value),
            )
        }

    fun updateMetadataTitle(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        current.copy(metadataDraft = current.metadataDraft.copy(title = value))

    fun updateMetadataDescription(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        current.copy(metadataDraft = current.metadataDraft.copy(description = value))

    fun updateMetadataTags(
        current: ModuleEditorPageState,
        value: List<String>,
    ): ModuleEditorPageState =
        current.copy(metadataDraft = current.metadataDraft.copy(tags = value))

    fun updateMetadataHiddenFromUi(
        current: ModuleEditorPageState,
        value: Boolean,
    ): ModuleEditorPageState =
        current.copy(metadataDraft = current.metadataDraft.copy(hiddenFromUi = value))

    fun openCreateModuleDialog(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(
            createModuleDialogOpen = true,
            createModuleDraft = CreateModuleDraft(),
            errorMessage = null,
            successMessage = null,
        )

    fun closeCreateModuleDialog(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(
            createModuleDialogOpen = false,
            createModuleDraft = CreateModuleDraft(),
        )

    fun updateCreateModuleCode(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        current.copy(createModuleDraft = current.createModuleDraft.copy(moduleCode = value))

    fun updateCreateModuleTitle(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        current.copy(createModuleDraft = current.createModuleDraft.copy(title = value))

    fun updateCreateModuleDescription(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        current.copy(createModuleDraft = current.createModuleDraft.copy(description = value))

    fun updateCreateModuleTagsText(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        current.copy(createModuleDraft = current.createModuleDraft.copy(tagsText = value))

    fun updateCreateModuleHiddenFromUi(
        current: ModuleEditorPageState,
        value: Boolean,
    ): ModuleEditorPageState =
        current.copy(createModuleDraft = current.createModuleDraft.copy(hiddenFromUi = value))

    fun updateCreateModuleConfigText(
        current: ModuleEditorPageState,
        value: String,
    ): ModuleEditorPageState =
        current.copy(createModuleDraft = current.createModuleDraft.copy(configText = value))

    fun restoreCreateModuleTemplate(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(
            createModuleDraft = current.createModuleDraft.copy(configText = defaultCreateModuleConfigTemplate()),
        )

    fun updateConfigFormLocally(
        current: ModuleEditorPageState,
        formState: ConfigFormStateDto,
    ): ModuleEditorPageState =
        current.copy(
            configFormState = formState,
            configFormError = null,
        )

    fun startLoading(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(loading = true, errorMessage = null, successMessage = null)

    fun beginAction(
        current: ModuleEditorPageState,
        actionName: String,
    ): ModuleEditorPageState =
        current.copy(actionInProgress = actionName, errorMessage = null, successMessage = null)
}
