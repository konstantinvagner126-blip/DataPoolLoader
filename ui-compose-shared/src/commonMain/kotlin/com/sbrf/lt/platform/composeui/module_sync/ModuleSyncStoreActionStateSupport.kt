package com.sbrf.lt.platform.composeui.module_sync

internal class ModuleSyncStoreActionStateSupport {
    fun applySuccess(
        reloaded: ModuleSyncPageState,
        successMessage: String,
    ): ModuleSyncPageState =
        reloaded.copy(
            successMessage = successMessage,
            actionInProgress = null,
        )

    fun applyFailure(
        current: ModuleSyncPageState,
        error: Throwable,
        fallbackMessage: String,
    ): ModuleSyncPageState =
        current.copy(
            actionInProgress = null,
            errorMessage = error.message ?: fallbackMessage,
        )
}
