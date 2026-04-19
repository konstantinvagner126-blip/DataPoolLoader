package com.sbrf.lt.platform.composeui.run_history_cleanup

import com.sbrf.lt.platform.composeui.foundation.format.formatByteSize
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode

internal fun buildSubtitle(storageMode: ModuleStoreMode?): String =
    when (storageMode) {
        ModuleStoreMode.DATABASE ->
            "Cleanup истории и output-каталогов DB-запусков по retention policy с preview перед удалением."
        ModuleStoreMode.FILES ->
            "Cleanup истории и output-каталогов файловых запусков по retention policy."
        null ->
            "Очистка истории запусков и output-каталогов в зависимости от выбранного режима UI."
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
    preview.estimatedBytesToFree?.let(::formatByteSize) ?: "Через VACUUM"

internal fun buildCleanupFreedNote(preview: RunHistoryCleanupPreviewResponse): String =
    if (preview.storageMode == "DATABASE") {
        "DELETE уменьшит данные логически, а физическое место PostgreSQL вернет после autovacuum/VACUUM."
    } else {
        "Оценка уменьшения persisted history после cleanup preview."
    }
