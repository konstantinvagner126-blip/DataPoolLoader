package com.sbrf.lt.datapool.sqlconsole

import com.fasterxml.jackson.annotation.JsonAlias

data class SqlConsoleConfig(
    val fetchSize: Int = 1000,
    val maxRowsPerShard: Int = 200,
    val queryTimeoutSec: Int? = null,
    @param:JsonAlias("shards")
    val sources: List<SqlConsoleSourceConfig> = emptyList(),
)

data class SqlConsoleSourceConfig(
    val name: String = "",
    val jdbcUrl: String = "",
    val username: String = "",
    val password: String = "",
)

data class ResolvedSqlConsoleShardConfig(
    val name: String,
    val jdbcUrl: String,
    val username: String,
    val password: String,
)

enum class SqlConsoleStatementType {
    RESULT_SET,
    COMMAND,
}

data class SqlConsoleStatement(
    val sql: String,
    val leadingKeyword: String,
)

data class RawShardExecutionResult(
    val shardName: String,
    val status: String,
    val columns: List<String> = emptyList(),
    val rows: List<Map<String, String?>> = emptyList(),
    val truncated: Boolean = false,
    val affectedRows: Int? = null,
    val message: String? = null,
    val errorMessage: String? = null,
)

data class RawShardConnectionCheckResult(
    val shardName: String,
    val status: String,
    val message: String? = null,
    val errorMessage: String? = null,
)

data class SqlConsoleInfo(
    val configured: Boolean,
    val sourceNames: List<String>,
    val maxRowsPerShard: Int,
    val queryTimeoutSec: Int?,
)

data class SqlConsoleQueryResult(
    val sql: String,
    val statementType: SqlConsoleStatementType,
    val statementKeyword: String,
    val shardResults: List<RawShardExecutionResult>,
    val maxRowsPerShard: Int,
)

data class SqlConsoleConnectionCheckResult(
    val sourceResults: List<RawShardConnectionCheckResult>,
)
