package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Состояние одного источника внутри DB-запуска.
 */
data class DatabaseRunSourceResultResponse(
    val runSourceResultId: String,
    val sourceName: String,
    val sortOrder: Int,
    val status: String,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val exportedRowCount: Long? = null,
    val mergedRowCount: Long? = null,
    val errorMessage: String? = null,
)
