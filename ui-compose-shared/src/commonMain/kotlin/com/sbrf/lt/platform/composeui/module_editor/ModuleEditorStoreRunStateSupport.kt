package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreRunStateSupport {
    fun runStarted(current: ModuleEditorPageState): ModuleEditorPageState =
        current.copy(
            actionInProgress = null,
            errorMessage = null,
            successMessage = null,
        )

    fun runFailed(
        current: ModuleEditorPageState,
        error: Throwable,
        fallbackMessage: String,
    ): ModuleEditorPageState =
        current.copy(
            actionInProgress = null,
            errorMessage = error.message ?: fallbackMessage,
        )
}
