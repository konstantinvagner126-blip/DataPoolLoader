package com.sbrf.lt.platform.composeui.sql_console

@kotlinx.serialization.Serializable
internal data class SqlConsoleExportRequest(
    val result: SqlConsoleQueryResult,
    val shardName: String? = null,
)

internal fun SqlConsoleQueryResult?.statementResultsOrSelf(): List<SqlConsoleStatementResult> =
    when {
        this == null -> emptyList()
        statementResults.isNotEmpty() -> statementResults
        else -> listOf(
            SqlConsoleStatementResult(
                sql = sql,
                statementType = statementType,
                statementKeyword = statementKeyword,
                shardResults = shardResults,
            ),
        )
    }

internal fun SqlConsoleStatementResult.toStandaloneQueryResult(source: SqlConsoleQueryResult?): SqlConsoleQueryResult =
    SqlConsoleQueryResult(
        sql = sql,
        statementType = statementType,
        statementKeyword = statementKeyword,
        shardResults = shardResults,
        maxRowsPerShard = source?.maxRowsPerShard ?: 0,
        statementResults = emptyList(),
    )
