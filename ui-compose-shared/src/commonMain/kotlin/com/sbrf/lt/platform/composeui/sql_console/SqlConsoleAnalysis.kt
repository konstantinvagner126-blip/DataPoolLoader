package com.sbrf.lt.platform.composeui.sql_console

data class SqlStatementAnalysis(
    val keyword: String,
    val readOnly: Boolean,
    val dangerous: Boolean,
)

fun analyzeSqlStatement(sql: String): SqlStatementAnalysis {
    val normalized = normalizeSqlForAnalysis(sql)
    val keyword = extractLeadingKeyword(normalized)
    if (keyword.isBlank()) {
        return SqlStatementAnalysis(
            keyword = "SQL",
            readOnly = false,
            dangerous = false,
        )
    }
    if (keyword == "EXPLAIN") {
        return analyzeExplainStatement(normalized)
    }
    val readOnly = keyword in READ_ONLY_KEYWORDS
    val dangerous = keyword in DANGEROUS_KEYWORDS
    return SqlStatementAnalysis(
        keyword = keyword,
        readOnly = readOnly,
        dangerous = dangerous,
    )
}

private fun normalizeSqlForAnalysis(sql: String): String =
    sql
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

private fun analyzeExplainStatement(normalizedSql: String): SqlStatementAnalysis {
    val explainBody = normalizedSql
        .trimStart()
        .split(Regex("\\s+"), limit = 2)
        .getOrElse(1) { "" }
        .trimStart()
    val (optionBlock, statementBody) = splitExplainBody(explainBody)
    val analyze = optionBlock.contains("ANALYZE", ignoreCase = true)
    val targetKeyword = extractLeadingKeyword(statementBody)
    val targetReadOnly = targetKeyword in READ_ONLY_KEYWORDS
    val targetDangerous = targetKeyword in DANGEROUS_KEYWORDS
    val displayKeyword = buildExplainKeyword(analyze, targetKeyword)
    return SqlStatementAnalysis(
        keyword = displayKeyword,
        readOnly = !analyze || targetReadOnly,
        dangerous = analyze && targetDangerous,
    )
}

private fun splitExplainBody(explainBody: String): Pair<String, String> {
    val trimmed = explainBody.trimStart()
    if (!trimmed.startsWith("(")) {
        return if (trimmed.startsWith("ANALYZE ", ignoreCase = true)) {
            "ANALYZE" to trimmed.substringAfter(' ', "")
        } else {
            "" to trimmed
        }
    }
    var depth = 0
    for (index in trimmed.indices) {
        when (trimmed[index]) {
            '(' -> depth += 1
            ')' -> {
                depth -= 1
                if (depth == 0) {
                    return trimmed.substring(0, index + 1) to trimmed.substring(index + 1).trimStart()
                }
            }
        }
    }
    return trimmed to ""
}

private fun buildExplainKeyword(
    analyze: Boolean,
    targetKeyword: String,
): String =
    buildString {
        append("EXPLAIN")
        if (analyze) {
            append(" ANALYZE")
        }
        if (targetKeyword.isNotBlank()) {
            append(' ')
            append(targetKeyword)
        }
    }

private fun extractLeadingKeyword(normalized: String): String {
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
