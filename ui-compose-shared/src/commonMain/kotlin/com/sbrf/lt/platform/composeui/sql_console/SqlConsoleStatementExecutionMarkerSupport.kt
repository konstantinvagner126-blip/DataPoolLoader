package com.sbrf.lt.platform.composeui.sql_console

data class SqlConsoleStatementExecutionMarker(
    val statementSql: String,
    val statementKeyword: String,
    val status: String,
    val title: String,
    val details: List<String> = emptyList(),
)

fun buildSqlConsoleStatementExecutionMarkers(
    execution: SqlConsoleExecutionResponse?,
): List<SqlConsoleStatementExecutionMarker> {
    val statements = execution.statementResultsOrSelf()
    if (statements.isEmpty()) {
        return emptyList()
    }
    return statements.map { statement ->
        val status = classifyStatementExecutionMarkerStatus(execution, statement)
        val failedShards = statement.shardResults.filter { it.status.equals("FAILED", ignoreCase = true) }
        val skippedShards = statement.shardResults.filter { it.status.equals("SKIPPED", ignoreCase = true) }
        val successShards = statement.shardResults.filter { it.status.equals("SUCCESS", ignoreCase = true) }
        val totalRows = successShards.sumOf { it.rowCount }
        val totalAffectedRows = successShards.mapNotNull { it.affectedRows }.takeIf { it.isNotEmpty() }?.sum()
        val maxDurationMillis = successShards.mapNotNull { it.durationMillis }.maxOrNull()
        val analysis = analyzeSqlStatement(statement.sql)

        SqlConsoleStatementExecutionMarker(
            statementSql = statement.sql,
            statementKeyword = statement.statementKeyword,
            status = status,
            title = buildStatementExecutionMarkerTitle(statement.statementKeyword, status),
            details = buildList {
                add(
                    "Source: ${successShards.size}/${statement.shardResults.size} success" +
                        buildFailedShardSuffix(failedShards) +
                        buildSkippedShardSuffix(skippedShards),
                )
                if (statement.statementType == "RESULT_SET" && totalRows > 0) {
                    add("Rows: $totalRows")
                }
                if (statement.statementType != "RESULT_SET" && totalAffectedRows != null) {
                    add("Affected rows: $totalAffectedRows")
                }
                if (maxDurationMillis != null) {
                    add("Max duration: ${formatStatementMarkerDuration(maxDurationMillis)}")
                }
                if (failedShards.isNotEmpty()) {
                    add("Failed source: ${failedShards.joinToString(", ") { it.shardName }}")
                }
                if (status == "pending_commit" && !analysis.readOnly) {
                    add("Транзакция ожидает Commit или Rollback.")
                }
            },
        )
    }
}

private fun SqlConsoleExecutionResponse?.statementResultsOrSelf(): List<SqlConsoleStatementResult> {
    val result = this?.result ?: return emptyList()
    return when {
        result.statementResults.isNotEmpty() -> result.statementResults
        else -> listOf(
            SqlConsoleStatementResult(
                sql = result.sql,
                statementType = result.statementType,
                statementKeyword = result.statementKeyword,
                shardResults = result.shardResults,
            ),
        )
    }
}

private fun classifyStatementExecutionMarkerStatus(
    execution: SqlConsoleExecutionResponse?,
    statement: SqlConsoleStatementResult,
): String {
    val analysis = analyzeSqlStatement(statement.sql)
    val shardResults = statement.shardResults
    return when {
        execution?.transactionState == "PENDING_COMMIT" && !analysis.readOnly -> "pending_commit"
        shardResults.any { it.status.equals("FAILED", ignoreCase = true) } -> "failed"
        execution?.status.equals("CANCELLED", ignoreCase = true) -> "cancelled"
        shardResults.any { it.status.equals("RUNNING", ignoreCase = true) } -> "running"
        shardResults.any { it.status.equals("SKIPPED", ignoreCase = true) } &&
            shardResults.any { it.status.equals("SUCCESS", ignoreCase = true) } -> "partial"
        shardResults.any { it.status.equals("SKIPPED", ignoreCase = true) } -> "skipped"
        else -> "success"
    }
}

private fun buildStatementExecutionMarkerTitle(
    statementKeyword: String,
    status: String,
): String =
    when (status) {
        "pending_commit" -> "$statementKeyword · pending commit"
        "failed" -> "$statementKeyword · error"
        "cancelled" -> "$statementKeyword · cancelled"
        "running" -> "$statementKeyword · running"
        "partial" -> "$statementKeyword · partial"
        "skipped" -> "$statementKeyword · skipped"
        else -> "$statementKeyword · ok"
    }

private fun buildFailedShardSuffix(failedShards: List<SqlConsoleShardResult>): String =
    if (failedShards.isEmpty()) {
        ""
    } else {
        ", ${failedShards.size} failed"
    }

private fun buildSkippedShardSuffix(skippedShards: List<SqlConsoleShardResult>): String =
    if (skippedShards.isEmpty()) {
        ""
    } else {
        ", ${skippedShards.size} skipped"
    }

private fun formatStatementMarkerDuration(durationMillis: Long): String =
    when {
        durationMillis < 1000 -> "${durationMillis} ms"
        durationMillis < 60_000 -> "${durationMillis / 1000.0}s"
        else -> "${durationMillis / 60_000} min"
    }
