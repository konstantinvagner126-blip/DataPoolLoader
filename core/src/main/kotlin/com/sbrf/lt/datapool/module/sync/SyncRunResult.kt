package com.sbrf.lt.datapool.module.sync

import java.time.Instant

/**
 * Итог одного запуска импорта `files -> database` со сводной статистикой по обработанным модулям.
 */
data class SyncRunResult(
    val syncRunId: String,
    val scope: String,
    val moduleCode: String? = null,
    val status: String,
    val startedAt: Instant,
    val finishedAt: Instant,
    val items: List<SyncItemResult>,
    val totalProcessed: Int,
    val totalCreated: Int,
    val totalUpdated: Int,
    val totalSkipped: Int,
    val totalFailed: Int,
    val errorMessage: String? = null,
)
