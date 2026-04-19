package com.sbrf.lt.platform.composeui.home

import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeContext

internal fun buildModeStatusText(runtime: RuntimeContext?): String {
    if (runtime == null) {
        return ""
    }
    val parts = mutableListOf<String>()
    parts += if (runtime.database.available) {
        "PostgreSQL доступен"
    } else {
        "PostgreSQL недоступен"
    }
    if (runtime.requestedMode != runtime.effectiveMode) {
        parts += "запрошен режим ${if (runtime.requestedMode == ModuleStoreMode.DATABASE) "«База данных»" else "«Файлы»"}"
    }
    return parts.joinToString(" · ")
}

internal fun parseModeAccessError(search: String): String? {
    if (search.isBlank()) {
        return null
    }
    return js("new URLSearchParams(search).get('modeAccessError')") as String?
}

internal fun buildModeAccessAlertText(
    modeAccessError: String,
    runtime: RuntimeContext?,
): String =
    when (modeAccessError) {
        "modules" -> "Страница файловых модулей доступна только в режиме «Файлы»."
        "db-modules",
        "db-sync",
        -> {
            val suffix = runtime?.fallbackReason?.let {
                " Текущая причина переключения в файловый режим: $it"
            }.orEmpty()
            "Страницы режима «База данных» доступны только в режиме «База данных».$suffix"
        }

        else -> "Страница недоступна в текущем режиме UI."
    }
