package com.sbrf.lt.platform.ui.model

/**
 * Результат выполнения SQL на одном shard/source.
 */
data class SqlConsoleShardResultResponse(
    val shardName: String,
    val status: String,
    val rows: List<Map<String, String?>> = emptyList(),
    val rowCount: Int = 0,
    val columns: List<String> = emptyList(),
    val truncated: Boolean = false,
    val affectedRows: Int? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val connectionState: String? = null,
    val startedAt: java.time.Instant? = null,
    val finishedAt: java.time.Instant? = null,
    val durationMillis: Long? = null,
)
