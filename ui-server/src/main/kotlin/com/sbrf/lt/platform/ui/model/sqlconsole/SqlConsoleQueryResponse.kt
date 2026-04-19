package com.sbrf.lt.platform.ui.model

/**
 * Итог выполнения SQL-запроса по всем выбранным shard/source.
 */
data class SqlConsoleQueryResponse(
    val sql: String,
    val statementType: String,
    val statementKeyword: String,
    val shardResults: List<SqlConsoleShardResultResponse>,
    val maxRowsPerShard: Int,
    val statementResults: List<SqlConsoleStatementResultResponse> = emptyList(),
)
