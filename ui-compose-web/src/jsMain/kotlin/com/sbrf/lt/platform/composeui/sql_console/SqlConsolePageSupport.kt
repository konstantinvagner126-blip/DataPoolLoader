package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import kotlinx.browser.window
import org.w3c.files.File
import org.w3c.xhr.FormData

internal fun buildSqlConsoleFallbackWarning(runtimeContext: com.sbrf.lt.platform.composeui.model.RuntimeContext): String {
    val requestedLabel = if (runtimeContext.requestedMode == ModuleStoreMode.DATABASE) "База данных" else "Файлы"
    val effectiveLabel = if (runtimeContext.effectiveMode == ModuleStoreMode.DATABASE) "База данных" else "Файлы"
    val reason = runtimeContext.fallbackReason?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    return "Запрошен режим «$requestedLabel», но сейчас активен «$effectiveLabel». SQL-консоль доступна, однако экраны модулей работают по текущему runtime-context.$reason"
}

internal suspend fun loadCredentialsStatus(
    httpClient: ComposeHttpClient,
): CredentialsStatusResponse? =
    runCatching {
        httpClient.get("/api/credentials", CredentialsStatusResponse.serializer())
    }.getOrNull()

internal suspend fun uploadCredentialsFile(
    httpClient: ComposeHttpClient,
    file: File,
): CredentialsStatusResponse {
    val formData = FormData()
    formData.append("file", file, file.name)
    return httpClient.postFormData(
        path = "/api/credentials/upload",
        formData = formData,
        deserializer = CredentialsStatusResponse.serializer(),
    )
}

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

internal fun focusEditorLine(
    editor: dynamic,
    lineNumber: Int,
) {
    if (editor == null) {
        return
    }
    editor.revealLineInCenter(lineNumber)
    val position = js("{}")
    position.lineNumber = lineNumber
    position.column = 1
    editor.setPosition(position)
    editor.focus()
}

internal fun insertSqlText(
    editor: dynamic,
    text: String,
    currentValue: String,
    onFallback: (String) -> Unit,
) {
    if (editor == null || editor.executeEdits == undefined) {
        onFallback(appendSqlText(currentValue, text))
        return
    }
    val edit = js("{}")
    edit.range = editor.getSelection()
    edit.text = text
    edit.forceMoveMarkers = true
    editor.executeEdits("compose-sql-console-favorites", arrayOf(edit))
    editor.focus()
}

internal fun appendSqlText(
    currentValue: String,
    text: String,
): String =
    when {
        currentValue.isBlank() -> text
        currentValue.last().isWhitespace() -> currentValue + text
        else -> "$currentValue $text"
    }

internal fun registerSqlConsoleEditorShortcuts(
    editor: dynamic,
    onRun: () -> Unit,
    onRunCurrent: () -> Unit,
    onFormat: () -> Unit,
    onStop: () -> Unit,
) {
    val monaco = window.asDynamic().monaco ?: return
    val ctrlEnter = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyCode.Enter as Int)
    val ctrlShiftEnter = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyMod.Shift as Int) or (monaco.KeyCode.Enter as Int)
    val shiftAltF = (monaco.KeyMod.Shift as Int) or (monaco.KeyMod.Alt as Int) or (monaco.KeyCode.KeyF as Int)
    val escape = monaco.KeyCode.Escape as Int
    editor.addCommand(ctrlEnter) { onRun() }
    editor.addCommand(ctrlShiftEnter) { onRunCurrent() }
    editor.addCommand(shiftAltF) { onFormat() }
    editor.addCommand(escape) { onStop() }
}

internal fun SqlConsoleFavoriteObject.qualifiedName(): String = "${schemaName}.${objectName}"

internal fun supportsFavoriteRowPreview(favorite: SqlConsoleFavoriteObject): Boolean =
    when (favorite.objectType.uppercase()) {
        "TABLE", "VIEW", "MATERIALIZED_VIEW" -> true
        else -> false
    }

internal fun buildFavoritePreviewSql(favorite: SqlConsoleFavoriteObject): String {
    val qualifiedName = sqlQualifiedName(favorite.schemaName, favorite.objectName)
    return if (supportsFavoriteRowPreview(favorite)) {
        """
        select *
        from $qualifiedName
        limit 100;
        """.trimIndent()
    } else {
        """
        select schemaname,
               tablename,
               indexname,
               indexdef
        from pg_catalog.pg_indexes
        where schemaname = ${sqlLiteral(favorite.schemaName)}
          and indexname = ${sqlLiteral(favorite.objectName)};
        """.trimIndent()
    }
}

internal fun buildFavoriteCountSql(favorite: SqlConsoleFavoriteObject): String {
    val qualifiedName = sqlQualifiedName(favorite.schemaName, favorite.objectName)
    return """
        select count(*) as total_rows
        from $qualifiedName;
    """.trimIndent()
}

internal fun buildFavoriteMetadataHref(favorite: SqlConsoleFavoriteObject): String =
    "/sql-console-objects?query=${urlEncode(favorite.objectName)}&source=${urlEncode(favorite.sourceName)}&schema=${urlEncode(favorite.schemaName)}&object=${urlEncode(favorite.objectName)}&type=${urlEncode(favorite.objectType)}"

internal fun sqlQualifiedName(
    schemaName: String,
    objectName: String,
): String = "${sqlIdentifier(schemaName)}.${sqlIdentifier(objectName)}"

internal fun sqlIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""

internal fun sqlLiteral(value: String): String = "'${value.replace("'", "''")}'"

internal fun urlEncode(value: String): String = js("encodeURIComponent(value)") as String

internal fun translateFavoriteObjectType(type: String): String =
    when (type.uppercase()) {
        "TABLE" -> "Таблица"
        "VIEW" -> "Представление"
        "MATERIALIZED_VIEW" -> "Материализованное представление"
        "INDEX" -> "Индекс"
        else -> type
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

internal fun SqlConsoleStatementResult.toStandaloneQueryResult(source: SqlConsoleQueryResult?): SqlConsoleQueryResult =
    SqlConsoleQueryResult(
        sql = sql,
        statementType = statementType,
        statementKeyword = statementKeyword,
        shardResults = shardResults,
        maxRowsPerShard = source?.maxRowsPerShard ?: 0,
        statementResults = emptyList(),
    )
