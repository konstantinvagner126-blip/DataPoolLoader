package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Краткая сводка по одному запуску модуля.
 */
data class ModuleRunSummaryResponse(
    val runId: String,
    val moduleId: String,
    val moduleTitle: String,
    val status: String,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val requestedAt: Instant? = null,
    val outputDir: String? = null,
    val mergedRowCount: Long? = null,
    val errorMessage: String? = null,
    val launchSourceKind: String? = null,
    val executionSnapshotId: String? = null,
    val successfulSourceCount: Int? = null,
    val failedSourceCount: Int? = null,
    val skippedSourceCount: Int? = null,
    val targetStatus: String? = null,
    val targetTableName: String? = null,
    val targetRowsLoaded: Long? = null,
)
