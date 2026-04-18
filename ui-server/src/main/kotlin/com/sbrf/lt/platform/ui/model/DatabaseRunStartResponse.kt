package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Ответ на старт DB-запуска модуля.
 */
data class DatabaseRunStartResponse(
    val runId: String,
    val moduleCode: String,
    val status: String,
    val requestedAt: Instant,
    val launchSourceKind: String,
    val executionSnapshotId: String,
    val message: String,
)
