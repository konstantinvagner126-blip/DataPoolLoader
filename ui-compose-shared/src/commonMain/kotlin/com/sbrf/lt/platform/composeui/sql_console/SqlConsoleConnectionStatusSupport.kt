package com.sbrf.lt.platform.composeui.sql_console

internal fun mergeSourceConnectionStatuses(
    current: List<SqlConsoleSourceConnectionStatus>,
    observed: List<SqlConsoleSourceConnectionStatus>,
): List<SqlConsoleSourceConnectionStatus> {
    if (observed.isEmpty()) {
        return current
    }
    val merged = LinkedHashMap<String, SqlConsoleSourceConnectionStatus>()
    current.forEach { merged[it.sourceName] = it }
    observed.forEach { merged[it.sourceName] = it }
    return merged.values.toList()
}

internal fun observedExecutionSourceStatuses(result: SqlConsoleQueryResult?): List<SqlConsoleSourceConnectionStatus> {
    if (result == null) {
        return emptyList()
    }
    val latestBySource = LinkedHashMap<String, SqlConsoleShardResult>()
    result.statementResultsOrSelf().forEach { statement ->
        statement.shardResults.forEach { shard ->
            if (shard.connectionState != null) {
                latestBySource[shard.shardName] = shard
            }
        }
    }
    return latestBySource.values.mapNotNull { it.toObservedSourceStatus() }
}

private fun SqlConsoleQueryResult.statementResultsOrSelf(): List<SqlConsoleStatementResult> =
    when {
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

private fun SqlConsoleShardResult.toObservedSourceStatus(): SqlConsoleSourceConnectionStatus? =
    when {
        connectionState.equals("UNAVAILABLE", ignoreCase = true) -> SqlConsoleSourceConnectionStatus(
            sourceName = shardName,
            status = "FAILED",
            errorMessage = errorMessage ?: message ?: "Последний SQL подтвердил, что источник недоступен.",
        )

        connectionState.equals("AVAILABLE", ignoreCase = true) &&
            (status.equals("FAILED", ignoreCase = true) || status.equals("ERROR", ignoreCase = true)) -> {
            val details = errorMessage ?: message
            SqlConsoleSourceConnectionStatus(
                sourceName = shardName,
                status = "OK",
                message = buildString {
                    append("Подключение подтверждено последним выполнением SQL.")
                    if (!details.isNullOrBlank()) {
                        append(" Ошибка выполнения: ")
                        append(details)
                    }
                },
            )
        }

        connectionState.equals("AVAILABLE", ignoreCase = true) -> SqlConsoleSourceConnectionStatus(
            sourceName = shardName,
            status = "OK",
            message = "Подключение подтверждено последним выполнением SQL.",
        )

        else -> null
    }
