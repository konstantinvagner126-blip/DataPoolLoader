package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreDraftFieldSupport {
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

    fun updateConfigFormLocally(
        current: ModuleEditorPageState,
        formState: ConfigFormStateDto,
    ): ModuleEditorPageState =
        current.copy(
            configFormState = formState,
            configFormError = null,
        )
}
