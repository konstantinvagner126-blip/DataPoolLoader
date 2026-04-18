package com.sbrf.lt.platform.composeui.module_sync

fun translateSyncStatus(status: String): String =
    when (status.uppercase()) {
        "SUCCESS" -> "Успешно"
        "FAILED" -> "Ошибка"
        "RUNNING" -> "Выполняется"
        "PARTIAL_SUCCESS" -> "Частично успешно"
        else -> status
    }

fun translateSyncScope(scope: String): String =
    when (scope.uppercase()) {
        "ALL" -> "Все модули"
        "ONE" -> "Один модуль"
        else -> scope
    }

fun translateSyncAction(action: String): String =
    when (action.uppercase()) {
        "CREATED" -> "Создан"
        "UPDATED" -> "Обновлён"
        "SKIPPED" -> "Пропущен"
        "SKIPPED_NO_CHANGES" -> "Пропущен без изменений"
        "SKIPPED_CODE_CONFLICT" -> "Пропущен из-за конфликта кода"
        "FAILED" -> "Ошибка"
        else -> action
    }

fun syncRunTitle(run: ModuleSyncRunSummaryResponse): String =
    if (run.scope.equals("ONE", ignoreCase = true) && !run.moduleCode.isNullOrBlank()) {
        "Модуль ${run.moduleCode}"
    } else {
        translateSyncScope(run.scope)
    }

fun activeSingleSyncFor(
    moduleCode: String,
    syncState: ModuleSyncStateResponse?,
): ActiveModuleSyncRunResponse? {
    val normalized = moduleCode.trim()
    if (normalized.isBlank() || syncState == null) {
        return null
    }
    return syncState.activeSingleSyncs.firstOrNull { it.moduleCode == normalized }
}
