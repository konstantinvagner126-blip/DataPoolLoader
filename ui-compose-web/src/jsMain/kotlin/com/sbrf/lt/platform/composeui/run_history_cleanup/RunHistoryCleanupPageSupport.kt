package com.sbrf.lt.platform.composeui.run_history_cleanup

import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeContext

internal fun buildSubtitle(storageMode: ModuleStoreMode?): String =
    when (storageMode) {
        ModuleStoreMode.DATABASE ->
            "Cleanup истории и output-каталогов DB-запусков по retention policy с preview перед удалением."
        ModuleStoreMode.FILES ->
            "Cleanup истории и output-каталогов файловых запусков по retention policy."
        null ->
            "Очистка истории запусков и output-каталогов в зависимости от выбранного режима UI."
    }

internal fun buildFallbackWarning(runtimeContext: RuntimeContext): String =
    buildString {
        append("Выбран режим базы данных, но он сейчас недоступен.")
        runtimeContext.fallbackReason?.takeIf { it.isNotBlank() }?.let { reason ->
            append(" Причина: ")
            append(reason)
        }
        append(" Экран показывает состояние выбранного режима, а операции для БД будут недоступны до восстановления подключения.")
    }

internal fun buildSafeguardLabel(storageMode: ModuleStoreMode?): String =
    when (storageMode) {
        ModuleStoreMode.DATABASE,
        ModuleStoreMode.FILES,
        null,
        -> "Отключить safeguard и не сохранять минимум 30 запусков на модуль"
    }

internal fun buildRelatedRecordsNote(preview: RunHistoryCleanupPreviewResponse): String =
    if (preview.storageMode == "DATABASE") {
        "Source results: ${preview.totalSourceResultsToDelete} · artifacts: ${preview.totalArtifactsToDelete} · events: ${preview.totalEventsToDelete}"
    } else {
        "Для FILES удаляются встроенные события из сохраненных snapshot-ов."
    }

internal fun buildCurrentHistoryNote(preview: RunHistoryCleanupPreviewResponse): String =
    if (preview.storageMode == "DATABASE") {
        "Физический размер history-таблиц PostgreSQL в текущем режиме."
    } else {
        "Размер persisted history файла run-state.json."
    }

internal fun buildCleanupFreedValue(preview: RunHistoryCleanupPreviewResponse): String =
    preview.estimatedBytesToFree?.let(::formatBytes) ?: "Через VACUUM"

internal fun buildCleanupFreedNote(preview: RunHistoryCleanupPreviewResponse): String =
    if (preview.storageMode == "DATABASE") {
        "DELETE уменьшит данные логически, а физическое место PostgreSQL вернет после autovacuum/VACUUM."
    } else {
        "Оценка уменьшения persisted history после cleanup preview."
    }

internal fun formatInstant(value: String?): String =
    value?.replace("T", " ")?.removeSuffix("Z") ?: "—"

internal fun formatBytes(value: Long): String =
    when {
        value >= 1024L * 1024L * 1024L -> "${value / (1024L * 1024L * 1024L)} ГБ"
        value >= 1024L * 1024L -> "${value / (1024L * 1024L)} МБ"
        value >= 1024L -> "${value / 1024L} КБ"
        else -> "$value Б"
    }
