package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Снимок текущего состояния асинхронного SQL-запроса.
 */
data class SqlConsoleExecutionResponse(
    val id: String,
    val status: String,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val cancelRequested: Boolean,
    val result: SqlConsoleQueryResponse? = null,
    val errorMessage: String? = null,
)
