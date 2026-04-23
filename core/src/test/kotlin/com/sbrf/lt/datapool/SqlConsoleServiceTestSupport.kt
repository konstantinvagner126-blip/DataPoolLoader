package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleSourceConfig
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import java.nio.file.Path

internal fun testSource(name: String): SqlConsoleSourceConfig =
    SqlConsoleSourceConfig(name, "jdbc:test:$name", "user-$name", "pwd-$name")

internal fun createSqlConsoleCredentials(vararg entries: Pair<String, String>): Path =
    createTempDirectory("sql-console-credentials")
        .resolve("credential.properties")
        .apply {
            writeText(
                entries.joinToString(separator = "\n") { (key, value) -> "$key=$value" },
            )
        }

internal fun resultSetResult(
    shardName: String,
    columns: List<String>,
    rows: List<Map<String, String>>,
    truncated: Boolean = false,
): RawShardExecutionResult =
    RawShardExecutionResult(
        shardName = shardName,
        status = "SUCCESS",
        columns = columns,
        rows = rows,
        truncated = truncated,
    )

internal fun commandResult(
    shardName: String,
    affectedRows: Int,
    message: String = "ok",
): RawShardExecutionResult =
    RawShardExecutionResult(
        shardName = shardName,
        status = "SUCCESS",
        affectedRows = affectedRows,
        message = message,
    )
