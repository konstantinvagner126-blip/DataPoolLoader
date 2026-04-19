package com.sbrf.lt.platform.composeui.module_sync

import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime

internal fun buildMaintenanceMessage(syncState: ModuleSyncStateResponse): String {
    val active = syncState.activeFullSync
    val actor = actorLabel(active?.startedByActorDisplayName, active?.startedByActorId)
    val startedAt = active?.startedAt?.let(::formatDateTime)
    return listOf(
        syncState.message,
        actor?.let { "Инициатор: $it." },
        startedAt?.let { "Запуск: $it." },
    ).filterNotNull().joinToString(" ")
}

internal fun describeActiveSingleSync(sync: ActiveModuleSyncRunResponse): String {
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

internal fun buildActiveSingleSyncSummary(syncs: List<ActiveModuleSyncRunResponse>): String =
    syncs.joinToString(" ") { describeActiveSingleSync(it) }

internal fun syncRunMeta(run: ModuleSyncRunSummaryResponse): String {
    val actor = actorLabel(run.startedByActorDisplayName, run.startedByActorId)
    val finishedAt = run.finishedAt?.let(::formatDateTime) ?: "Запуск еще выполняется"
    return listOfNotNull(
        actor?.let { "Инициатор: $it" },
        "Завершение: $finishedAt",
    ).joinToString(" · ")
}
