package com.sbrf.lt.platform.composeui.module_sync

fun translateSyncStatus(status: String): String =
    when (status.uppercase()) {
        "SUCCESS" -> "Успешно"
        "FAILED" -> "Ошибка"
        "RUNNING" -> "Выполняется"
        "PARTIAL_SUCCESS" -> "Частично успешно"
        else -> status
    }

fun syncStatusTone(status: String): String =
    when (status.uppercase()) {
        "SUCCESS" -> "success"
        "FAILED" -> "danger"
        "RUNNING" -> "primary"
        "PARTIAL_SUCCESS" -> "warning"
        else -> "secondary"
    }

fun translateSyncScope(scope: String): String =
    when (scope.uppercase()) {
        "ALL" -> "Все модули"
        "ONE" -> "Один модуль"
        "SELECTED" -> "Выбранные модули"
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

fun syncActionTone(action: String): String =
    when (action.uppercase()) {
        "CREATED" -> "success"
        "UPDATED" -> "primary"
        "FAILED" -> "danger"
        "SKIPPED", "SKIPPED_NO_CHANGES", "SKIPPED_CODE_CONFLICT" -> "secondary"
        else -> "secondary"
    }

fun actorLabel(displayName: String?, actorId: String?): String? =
    displayName ?: actorId

fun buildMaintenanceMessage(
    syncState: ModuleSyncStateResponse,
    formatDateTime: (String?) -> String,
): String {
    val active = syncState.activeFullSync
    val actor = actorLabel(active?.startedByActorDisplayName, active?.startedByActorId)
    val startedAt = active?.startedAt?.let(formatDateTime)
    return listOf(
        syncState.message,
        actor?.let { "Инициатор: $it." },
        startedAt?.let { "Запуск: $it." },
    ).filterNotNull().joinToString(" ")
}

fun describeActiveSingleSync(
    sync: ActiveModuleSyncRunResponse,
    formatDateTime: (String?) -> String,
): String {
    val actor = actorLabel(sync.startedByActorDisplayName, sync.startedByActorId)
    val startedAt = formatDateTime(sync.startedAt)
    return buildString {
        append("Идет импорт модуля '${sync.moduleCode ?: "-"}'.")
        if (!actor.isNullOrBlank()) {
            append(" Инициатор: $actor.")
        }
        append(" Запуск: $startedAt.")
    }
}

fun buildActiveSingleSyncSummary(
    syncs: List<ActiveModuleSyncRunResponse>,
    formatDateTime: (String?) -> String,
): String = syncs.joinToString(" ") { describeActiveSingleSync(it, formatDateTime) }

fun syncRunMeta(
    run: ModuleSyncRunSummaryResponse,
    formatDateTime: (String?) -> String,
): String {
    val actor = actorLabel(run.startedByActorDisplayName, run.startedByActorId)
    val finishedAt = run.finishedAt?.let(formatDateTime) ?: "Запуск еще выполняется"
    return listOfNotNull(
        actor?.let { "Инициатор: $it" },
        "Завершение: $finishedAt",
    ).joinToString(" · ")
}

fun syncRunTitle(run: ModuleSyncRunSummaryResponse): String =
    if (run.scope.equals("ONE", ignoreCase = true) && !run.moduleCode.isNullOrBlank()) {
        "Модуль ${run.moduleCode}"
    } else if (run.scope.equals("SELECTED", ignoreCase = true)) {
        "Выбранные модули"
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
    return syncState.activeSingleSyncs.firstOrNull { sync ->
        sync.moduleCode
            ?.split(',')
            ?.map { it.trim() }
            ?.any { it == normalized } == true
    }
}
