package com.sbrf.lt.platform.composeui.module_sync

import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.model.ModuleCatalogItem

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

internal fun filterSelectableModules(state: ModuleSyncPageState): List<ModuleCatalogItem> =
    state.availableFileModules
        .sortedWith(compareBy<ModuleCatalogItem> { it.title.ifBlank { it.id } }.thenBy { it.id })
        .filter { module ->
            val query = state.moduleSearchQuery.trim()
            val description = module.description
            query.isBlank() ||
                module.id.contains(query, ignoreCase = true) ||
                module.title.contains(query, ignoreCase = true) ||
                (!description.isNullOrBlank() && description.contains(query, ignoreCase = true))
}

internal fun syncRunMeta(run: ModuleSyncRunSummaryResponse): String {
    val actor = actorLabel(run.startedByActorDisplayName, run.startedByActorId)
    val finishedAt = run.finishedAt?.let(::formatDateTime) ?: "Запуск еще выполняется"
    return listOfNotNull(
        actor?.let { "Инициатор: $it" },
        "Завершение: $finishedAt",
    ).joinToString(" · ")
}

internal fun syncStatusBadgeClass(status: String): String =
    when (status.uppercase()) {
        "SUCCESS" -> "bg-success"
        "FAILED" -> "bg-danger"
        "RUNNING" -> "bg-primary"
        "PARTIAL_SUCCESS" -> "bg-warning text-dark"
        else -> "bg-secondary"
    }

internal fun syncActionBadgeClass(action: String): String =
    when (action.uppercase()) {
        "CREATED" -> "bg-success"
        "UPDATED" -> "bg-primary"
        "SKIPPED", "SKIPPED_NO_CHANGES", "SKIPPED_CODE_CONFLICT" -> "bg-secondary"
        "FAILED" -> "bg-danger"
        else -> "bg-secondary"
    }

internal fun actorLabel(displayName: String?, actorId: String?): String? =
    displayName ?: actorId
