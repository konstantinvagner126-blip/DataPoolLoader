package com.sbrf.lt.platform.composeui.sql_console

internal enum class SqlConsoleExplainMode(
    val commandPrefix: String,
    val label: String,
) {
    PLAN(
        commandPrefix = "EXPLAIN",
        label = "EXPLAIN",
    ),
    ANALYZE(
        commandPrefix = "EXPLAIN ANALYZE",
        label = "EXPLAIN ANALYZE",
    ),
}

internal fun buildExplainSql(
    sql: String,
    mode: SqlConsoleExplainMode,
): String =
    "${mode.commandPrefix}\n${sql.trim()}"

internal fun resolveExplainSelectionSql(selectedSql: String): String? {
    val trimmedSelection = selectedSql.trim()
    if (trimmedSelection.isBlank()) {
        return null
    }
    val statements = parseSqlScriptOutline(trimmedSelection)
    return statements
        .singleOrNull()
        ?.sql
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

internal fun buildSqlExplainScopeSummary(
    currentOutlineItem: SqlScriptOutlineItem?,
    selectedSqlText: String,
    selectedSqlLineCount: Int,
): String =
    when {
        currentOutlineItem != null && selectedSqlText.isNotBlank() ->
            "Scope выбирается явно: current или выделение"
        selectedSqlText.isNotBlank() ->
            if (selectedSqlLineCount > 1) "Выделение: $selectedSqlLineCount строк" else "Выделение: 1 строка"
        currentOutlineItem != null ->
            "Под курсором: ${currentOutlineItem.keyword}"
        else -> "Нужен current statement или выделение"
    }
