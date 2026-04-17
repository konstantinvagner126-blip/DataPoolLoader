package com.sbrf.lt.datapool.module.sync

import java.time.Instant

/**
 * Краткая сводка по одному запуску импорта `files -> database`.
 */
data class ModuleSyncRunSummary(
    val syncRunId: String,
    val scope: String,
    val moduleCode: String? = null,
    val status: String,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val startedByActorId: String? = null,
    val startedByActorSource: String? = null,
    val startedByActorDisplayName: String? = null,
    val totalProcessed: Int = 0,
    val totalCreated: Int = 0,
    val totalUpdated: Int = 0,
    val totalSkipped: Int = 0,
    val totalFailed: Int = 0,
)
