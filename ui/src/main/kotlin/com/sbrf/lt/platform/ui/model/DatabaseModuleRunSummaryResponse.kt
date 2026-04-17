package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Краткая сводка по одному запуску DB-модуля.
 */
data class DatabaseModuleRunSummaryResponse(
    val runId: String,
    val executionSnapshotId: String,
    val status: String,
    val launchSourceKind: String,
    val requestedAt: Instant,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val moduleCode: String,
    val moduleTitle: String,
    val outputDir: String,
    val mergedRowCount: Long? = null,
    val successfulSourceCount: Int,
    val failedSourceCount: Int,
    val skippedSourceCount: Int,
    val targetStatus: String,
    val targetTableName: String? = null,
    val targetRowsLoaded: Long? = null,
    val errorMessage: String? = null,
)
