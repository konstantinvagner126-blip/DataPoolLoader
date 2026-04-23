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
    val autoCommitEnabled: Boolean = true,
    val transactionState: String = "NONE",
    val transactionShardNames: List<String> = emptyList(),
    val ownerToken: String? = null,
    val ownerLeaseExpiresAt: Instant? = null,
    val pendingCommitExpiresAt: Instant? = null,
    val result: SqlConsoleQueryResponse? = null,
    val errorMessage: String? = null,
)
