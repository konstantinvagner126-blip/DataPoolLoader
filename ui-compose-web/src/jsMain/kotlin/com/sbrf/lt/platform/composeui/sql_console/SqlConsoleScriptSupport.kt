package com.sbrf.lt.platform.composeui.sql_console

internal data class SqlScriptOutlineItem(
    val index: Int,
    val keyword: String,
    val readOnly: Boolean,
    val dangerous: Boolean,
    val startLine: Int,
    val endLine: Int,
    val sql: String,
    val preview: String,
)

internal fun parseSqlScriptOutline(sql: String): List<SqlScriptOutlineItem> {
    val items = mutableListOf<SqlScriptOutlineItem>()
    val current = StringBuilder()
    var index = 0
    var line = 1
    var statementStartLine = 1
    var inSingleQuote = false
    var inDoubleQuote = false
    var inLineComment = false
    var inBlockComment = false

    fun flushCurrent() {
        val raw = current.toString().trim()
        if (raw.isNotBlank()) {
            val analysis = analyzeSqlStatement(raw)
            val preview = raw.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                ?.take(120)
                .orEmpty()
            items += SqlScriptOutlineItem(
                index = items.size + 1,
                keyword = analysis.keyword,
                readOnly = analysis.readOnly,
                dangerous = analysis.dangerous,
                startLine = statementStartLine,
                endLine = line.coerceAtLeast(statementStartLine),
                sql = raw,
                preview = preview,
            )
        }
        current.clear()
    }

    while (index < sql.length) {
        val char = sql[index]
        val next = sql.getOrNull(index + 1)

        when {
            inLineComment -> {
                current.append(char)
                if (char == '\n') {
                    inLineComment = false
                }
            }

            inBlockComment -> {
                current.append(char)
                if (char == '*' && next == '/') {
                    current.append(next)
                    index++
                    inBlockComment = false
                }
            }

            inSingleQuote -> {
                current.append(char)
                if (char == '\'' && next == '\'') {
                    current.append(next)
                    index++
                } else if (char == '\'') {
                    inSingleQuote = false
                }
            }

            inDoubleQuote -> {
                current.append(char)
                if (char == '"' && next == '"') {
                    current.append(next)
                    index++
                } else if (char == '"') {
                    inDoubleQuote = false
                }
            }

            char == '-' && next == '-' -> {
                current.append(char).append(next)
                index++
                inLineComment = true
            }

            char == '/' && next == '*' -> {
                current.append(char).append(next)
                index++
                inBlockComment = true
            }

            char == '\'' -> {
                current.append(char)
                inSingleQuote = true
            }

            char == '"' -> {
                current.append(char)
                inDoubleQuote = true
            }

            char == ';' -> {
                flushCurrent()
                statementStartLine = line
            }

            else -> current.append(char)
        }

        if (char == '\n') {
            line += 1
            if (current.isEmpty()) {
                statementStartLine = line
            }
        }
        index++
    }

    flushCurrent()
    return items
}

internal fun formatSqlScript(sql: String): String {
    val outline = parseSqlScriptOutline(sql)
    if (outline.isEmpty()) {
        return sql.trim()
    }
    return outline.joinToString(separator = ";\n\n") { formatSqlStatement(it.sql) }.trim() +
        if (sql.trimEnd().endsWith(";")) ";" else ""
}

internal fun formatSqlStatement(sql: String): String {
    val whitespaceNormalized = sql
        .trim()
        .replace(Regex("\\s+"), " ")

    if (whitespaceNormalized.isBlank()) {
        return ""
    }

    var formatted = whitespaceNormalized
    listOf(
        "WITH",
        "SELECT",
        "FROM",
        "WHERE",
        "GROUP BY",
        "ORDER BY",
        "HAVING",
        "LIMIT",
        "OFFSET",
        "INSERT INTO",
        "UPDATE",
        "DELETE FROM",
        "VALUES",
        "SET",
        "RETURNING",
        "UNION ALL",
        "UNION",
        "LEFT JOIN",
        "RIGHT JOIN",
        "FULL JOIN",
        "INNER JOIN",
        "CROSS JOIN",
        "JOIN",
        "ON",
    ).forEachIndexed { index, keyword ->
        val prefix = if (index == 0) Regex("(?i)\\b$keyword\\b") else Regex("(?i)\\s+\\b$keyword\\b")
        formatted = formatted.replace(prefix, "\n${keyword.uppercase()}")
    }
    formatted = formatted
        .replace(Regex("\n+"), "\n")
        .lines()
        .joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("ON ") -> "    $trimmed"
                trimmed.startsWith("AND ") || trimmed.startsWith("OR ") -> "    $trimmed"
                else -> trimmed
            }
        }
        .trim()
    return formatted
}
