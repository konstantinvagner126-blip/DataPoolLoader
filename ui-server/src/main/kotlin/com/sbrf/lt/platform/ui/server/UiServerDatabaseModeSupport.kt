package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiRuntimeContext

internal fun UiServerContext.databaseModeUnavailableMessage(runtimeContext: UiRuntimeContext): String =
    buildString {
        append("Режим базы данных сейчас недоступен.")
        runtimeContext.fallbackReason?.takeIf { it.isNotBlank() }?.let { reason ->
            append(" Причина: ")
            append(reason)
        }
    }

internal fun UiServerContext.requireDatabaseMode(runtimeContext: UiRuntimeContext) {
    if (runtimeContext.effectiveMode != UiModuleStoreMode.DATABASE) {
        serviceUnavailable(databaseModeUnavailableMessage(runtimeContext))
    }
}
