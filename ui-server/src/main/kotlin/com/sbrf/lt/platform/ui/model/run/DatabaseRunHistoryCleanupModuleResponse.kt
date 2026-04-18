package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Сводка по модулю для preview cleanup истории DB-запусков.
 */
data class DatabaseRunHistoryCleanupModuleResponse(
    val moduleCode: String,
    val totalRunsToDelete: Int,
    val oldestRequestedAt: Instant? = null,
    val newestRequestedAt: Instant? = null,
)
