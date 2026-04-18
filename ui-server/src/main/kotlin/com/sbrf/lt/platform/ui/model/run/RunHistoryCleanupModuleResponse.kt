package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Агрегат cleanup-preview/result по одному модулю.
 */
data class RunHistoryCleanupModuleResponse(
    val moduleCode: String,
    val totalRunsToDelete: Int,
    val oldestRequestedAt: Instant? = null,
    val newestRequestedAt: Instant? = null,
)
