package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Краткий ответ после запуска асинхронного SQL-запроса.
 */
data class SqlConsoleStartQueryResponse(
    val id: String,
    val status: String,
    val startedAt: Instant,
    val cancelRequested: Boolean,
)
