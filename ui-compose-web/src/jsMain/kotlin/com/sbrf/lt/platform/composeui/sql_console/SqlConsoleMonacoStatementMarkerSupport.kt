package com.sbrf.lt.platform.composeui.sql_console

import kotlinx.browser.window

private const val SQL_CONSOLE_MONACO_NAMESPACE = "ComposeMonaco"

internal fun updateSqlConsoleMonacoStatementMarkers(
    editor: Any?,
    draftSql: String,
    execution: SqlConsoleExecutionResponse?,
) {
    val composeMonaco = window.asDynamic()[SQL_CONSOLE_MONACO_NAMESPACE] ?: return
    if (composeMonaco.setSqlStatementMarkers == undefined) {
        return
    }
    composeMonaco.setSqlStatementMarkers(
        editor,
        buildSqlConsoleMonacoStatementMarkers(draftSql, execution).toTypedArray(),
    )
}

internal fun sqlConsoleStatementMarkerEffectKey(execution: SqlConsoleExecutionResponse?): String =
    buildString {
        append(execution?.id.orEmpty())
        append("|")
        append(execution?.status.orEmpty())
        append("|")
        append(execution?.transactionState.orEmpty())
        append("|")
        append(execution?.cancelRequested ?: false)
        append("|")
        execution.statementResultsOrSelf().forEach { statement ->
            append(statement.statementKeyword)
            append(":")
            statement.shardResults.forEach { shard ->
                append(shard.shardName)
                append("=")
                append(shard.status)
                append("/")
                append(shard.rowCount)
                append("/")
                append(shard.affectedRows ?: "")
                append("/")
                append(shard.durationMillis ?: "")
                append(";")
            }
            append("|")
        }
    }

private fun buildSqlConsoleMonacoStatementMarkers(
    draftSql: String,
    execution: SqlConsoleExecutionResponse?,
): List<dynamic> {
    val outline = parseSqlScriptOutline(draftSql)
    if (outline.isEmpty()) {
        return emptyList()
    }

    val markerModels = buildSqlConsoleStatementExecutionMarkers(execution)
    if (markerModels.isEmpty()) {
        return if (execution?.status.equals("RUNNING", ignoreCase = true) && outline.size == 1) {
            listOf(
                createSqlConsoleMonacoStatementMarker(
                    startLine = outline.single().startLine,
                    endLine = outline.single().endLine,
                    status = "running",
                    title = "${outline.single().keyword} · running",
                    details = listOf("Запрос выполняется."),
                ),
            )
        } else {
            emptyList()
        }
    }

    return matchSqlConsoleStatementMarkers(outline, markerModels).map { (outlineItem, markerModel) ->
        createSqlConsoleMonacoStatementMarker(
            startLine = outlineItem.startLine,
            endLine = outlineItem.endLine,
            status = markerModel.status,
            title = markerModel.title,
            details = markerModel.details,
        )
    }
}

private fun matchSqlConsoleStatementMarkers(
    outline: List<SqlScriptOutlineItem>,
    markers: List<SqlConsoleStatementExecutionMarker>,
): List<Pair<SqlScriptOutlineItem, SqlConsoleStatementExecutionMarker>> {
    val remainingIndexes = outline.indices.toMutableList()
    return markers.mapNotNull { marker ->
        val markerSqlKey = normalizeSqlConsoleStatementMarkerSql(marker.statementSql)
        val matchedIndex = remainingIndexes.firstOrNull { index ->
            normalizeSqlConsoleStatementMarkerSql(outline[index].sql) == markerSqlKey
        } ?: remainingIndexes.firstOrNull()
        matchedIndex?.also { remainingIndexes.remove(it) }?.let { outline[it] to marker }
    }
}

private fun normalizeSqlConsoleStatementMarkerSql(value: String): String =
    value
        .lineSequence()
        .map { line ->
            val commentIndex = line.indexOf("--")
            if (commentIndex >= 0) {
                line.substring(0, commentIndex)
            } else {
                line
            }
        }
        .joinToString(" ")
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()

private fun createSqlConsoleMonacoStatementMarker(
    startLine: Int,
    endLine: Int,
    status: String,
    title: String,
    details: List<String>,
): dynamic {
    val marker = js("{}")
    marker.startLine = startLine
    marker.endLine = endLine
    marker.status = status
    marker.title = title
    marker.details = details.toTypedArray()
    return marker
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
