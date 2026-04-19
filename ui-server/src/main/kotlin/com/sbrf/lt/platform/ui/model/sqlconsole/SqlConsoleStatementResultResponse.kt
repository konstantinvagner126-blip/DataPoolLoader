package com.sbrf.lt.platform.ui.model

/**
 * Результат одного statement внутри SQL-скрипта по всем выбранным shard/source.
 */
data class SqlConsoleStatementResultResponse(
    val sql: String,
    val statementType: String,
    val statementKeyword: String,
    val shardResults: List<SqlConsoleShardResultResponse>,
)
