package com.sbrf.lt.platform.composeui.sql_console

data class SqlStatementAnalysis(
    val keyword: String,
    val readOnly: Boolean,
    val dangerous: Boolean,
)

fun analyzeSqlStatement(sql: String): SqlStatementAnalysis {
    val keyword = extractSqlKeyword(sql)
    if (keyword.isBlank()) {
        return SqlStatementAnalysis(
            keyword = "SQL",
            readOnly = false,
            dangerous = false,
        )
    }
    val readOnly = keyword in READ_ONLY_KEYWORDS
    val dangerous = keyword in DANGEROUS_KEYWORDS
    return SqlStatementAnalysis(
        keyword = keyword,
        readOnly = readOnly,
        dangerous = dangerous,
    )
}

private fun extractSqlKeyword(sql: String): String {
    val normalized = sql
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
    if (normalized.isBlank()) {
        return ""
    }
    return normalized
        .trimStart()
        .split(Regex("\\s+"), limit = 2)
        .firstOrNull()
        .orEmpty()
        .uppercase()
}

private val READ_ONLY_KEYWORDS = setOf(
    "SELECT",
    "WITH",
    "SHOW",
    "DESCRIBE",
    "DESC",
    "EXPLAIN",
    "VALUES",
)

private val DANGEROUS_KEYWORDS = setOf(
    "DELETE",
    "TRUNCATE",
    "DROP",
    "ALTER",
    "CREATE",
    "GRANT",
    "REVOKE",
)

