package com.sbrf.lt.platform.composeui.sql_console

data class SqlConsoleResultDiffView(
    val baselineSourceName: String,
    val sourceSummaries: List<SqlConsoleResultDiffSourceSummary>,
    val entries: List<SqlConsoleResultDiffEntry>,
    val totalMismatchCount: Int,
    val mismatchLimitReached: Boolean,
    val truncated: Boolean,
)

data class SqlConsoleResultDiffSourceSummary(
    val sourceName: String,
    val state: String,
    val rowCount: Int,
    val mismatchCount: Int,
    val errorMessage: String? = null,
)

data class SqlConsoleResultDiffEntry(
    val sourceName: String,
    val kind: String,
    val rowNumber: Int? = null,
    val columnName: String? = null,
    val baselineValue: String? = null,
    val sourceValue: String? = null,
    val message: String? = null,
)

fun buildSqlConsoleResultDiffView(
    result: SqlConsoleQueryResult,
    mismatchLimit: Int = 80,
): SqlConsoleResultDiffView? {
    if (result.statementType != "RESULT_SET") {
        return null
    }
    val successfulShards = result.shardResults.filter { it.status.equals("SUCCESS", ignoreCase = true) }
    val baseline = successfulShards.firstOrNull() ?: return null
    val truncated = successfulShards.any { it.truncated }
    val entries = mutableListOf<SqlConsoleResultDiffEntry>()
    val sourceSummaries = mutableListOf<SqlConsoleResultDiffSourceSummary>()
    var totalMismatchCount = 0

    result.shardResults.forEach { shard ->
        when {
            shard.shardName == baseline.shardName -> {
                sourceSummaries += SqlConsoleResultDiffSourceSummary(
                    sourceName = shard.shardName,
                    state = "BASELINE",
                    rowCount = shard.rowCount,
                    mismatchCount = 0,
                )
            }

            !shard.status.equals("SUCCESS", ignoreCase = true) -> {
                totalMismatchCount += 1
                appendDiffEntry(
                    entries = entries,
                    entry = SqlConsoleResultDiffEntry(
                        sourceName = shard.shardName,
                        kind = "SOURCE_FAILURE",
                        message = shard.errorMessage ?: shard.message ?: "Source завершился ошибкой.",
                    ),
                    mismatchLimit = mismatchLimit,
                )
                sourceSummaries += SqlConsoleResultDiffSourceSummary(
                    sourceName = shard.shardName,
                    state = "FAILED",
                    rowCount = shard.rowCount,
                    mismatchCount = 1,
                    errorMessage = shard.errorMessage ?: shard.message,
                )
            }

            else -> {
                val mismatchCount = compareShardAgainstBaseline(
                    baseline = baseline,
                    shard = shard,
                    entries = entries,
                    mismatchLimit = mismatchLimit,
                )
                totalMismatchCount += mismatchCount
                sourceSummaries += SqlConsoleResultDiffSourceSummary(
                    sourceName = shard.shardName,
                    state = if (mismatchCount == 0) "MATCH" else "MISMATCH",
                    rowCount = shard.rowCount,
                    mismatchCount = mismatchCount,
                )
            }
        }
    }

    return SqlConsoleResultDiffView(
        baselineSourceName = baseline.shardName,
        sourceSummaries = sourceSummaries,
        entries = entries,
        totalMismatchCount = totalMismatchCount,
        mismatchLimitReached = totalMismatchCount > entries.size,
        truncated = truncated,
    )
}

private fun compareShardAgainstBaseline(
    baseline: SqlConsoleShardResult,
    shard: SqlConsoleShardResult,
    entries: MutableList<SqlConsoleResultDiffEntry>,
    mismatchLimit: Int,
): Int {
    var mismatchCount = 0
    if (baseline.rowCount != shard.rowCount) {
        mismatchCount += 1
        appendDiffEntry(
            entries = entries,
            entry = SqlConsoleResultDiffEntry(
                sourceName = shard.shardName,
                kind = "ROW_COUNT",
                baselineValue = baseline.rowCount.toString(),
                sourceValue = shard.rowCount.toString(),
                message = "Row count отличается от baseline source ${baseline.shardName}.",
            ),
            mismatchLimit = mismatchLimit,
        )
    }
    val columns = (baseline.columns + shard.columns).distinct()
    val rowLimit = maxOf(baseline.rows.size, shard.rows.size)
    for (rowIndex in 0 until rowLimit) {
        val baselineRow = baseline.rows.getOrNull(rowIndex)
        val sourceRow = shard.rows.getOrNull(rowIndex)
        if (baselineRow == null || sourceRow == null) {
            mismatchCount += 1
            appendDiffEntry(
                entries = entries,
                entry = SqlConsoleResultDiffEntry(
                    sourceName = shard.shardName,
                    kind = if (baselineRow == null) "EXTRA_ROW" else "MISSING_ROW",
                    rowNumber = rowIndex + 1,
                    message = if (baselineRow == null) {
                        "В source есть лишняя строка по сравнению с baseline ${baseline.shardName}."
                    } else {
                        "В source отсутствует строка по сравнению с baseline ${baseline.shardName}."
                    },
                ),
                mismatchLimit = mismatchLimit,
            )
            continue
        }
        columns.forEach { columnName ->
            val baselineValue = baselineRow[columnName]
            val sourceValue = sourceRow[columnName]
            if (baselineValue != sourceValue) {
                mismatchCount += 1
                appendDiffEntry(
                    entries = entries,
                    entry = SqlConsoleResultDiffEntry(
                        sourceName = shard.shardName,
                        kind = "VALUE_MISMATCH",
                        rowNumber = rowIndex + 1,
                        columnName = columnName,
                        baselineValue = baselineValue,
                        sourceValue = sourceValue,
                    ),
                    mismatchLimit = mismatchLimit,
                )
            }
        }
    }
    return mismatchCount
}

private fun appendDiffEntry(
    entries: MutableList<SqlConsoleResultDiffEntry>,
    entry: SqlConsoleResultDiffEntry,
    mismatchLimit: Int,
) {
    if (entries.size < mismatchLimit) {
        entries += entry
    }
}
