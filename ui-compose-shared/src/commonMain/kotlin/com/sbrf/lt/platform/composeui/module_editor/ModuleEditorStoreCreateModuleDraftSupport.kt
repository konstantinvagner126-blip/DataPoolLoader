package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreCreateModuleDraftSupport {
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
}
