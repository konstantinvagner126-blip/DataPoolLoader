package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreDraftStatusSupport {
    fun clearSuccessMessage(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(successMessage = null)

    fun clearErrorMessage(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(errorMessage = null)

    fun startLoading(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(loading = true, errorMessage = null, successMessage = null)

    fun beginAction(
        current: ModuleEditorPageState,
        actionName: String,
    ): ModuleEditorPageState =
        current.copy(actionInProgress = actionName, errorMessage = null, successMessage = null)
}
