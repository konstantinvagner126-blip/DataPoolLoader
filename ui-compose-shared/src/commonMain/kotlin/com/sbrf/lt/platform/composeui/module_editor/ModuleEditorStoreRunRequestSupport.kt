package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreRunRequestSupport {
    fun buildFilesRunRequest(
        moduleId: String,
        state: ModuleEditorPageState,
    ): StartRunRequestDto =
        StartRunRequestDto(
            moduleId = moduleId,
            configText = state.configTextDraft,
            sqlFiles = state.sqlContentsDraft,
        )
}
