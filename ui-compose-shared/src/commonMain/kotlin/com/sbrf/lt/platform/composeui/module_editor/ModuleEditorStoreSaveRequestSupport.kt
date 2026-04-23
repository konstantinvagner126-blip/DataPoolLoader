package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreSaveRequestSupport {
    fun buildSaveRequest(state: ModuleEditorPageState): SaveModuleRequestDto =
        SaveModuleRequestDto(
            configText = state.configTextDraft,
            sqlFiles = state.sqlContentsDraft,
            title = state.metadataDraft.title,
            description = state.metadataDraft.description.ifBlank { null },
            tags = state.metadataDraft.tags,
            hiddenFromUi = state.metadataDraft.hiddenFromUi,
        )
}
