package com.sbrf.lt.datapool.module.sync

/**
 * Полные детали одного запуска импорта `files -> database`.
 */
data class ModuleSyncRunDetails(
    val run: ModuleSyncRunSummary,
    val items: List<SyncItemResult> = emptyList(),
)
