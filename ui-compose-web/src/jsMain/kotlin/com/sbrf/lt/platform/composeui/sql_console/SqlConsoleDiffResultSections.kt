package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

@Composable
internal fun SqlConsoleDiffResultPane(
    result: SqlConsoleQueryResult,
) {
    if (result.shardResults.none { it.status.equals("SUCCESS", ignoreCase = true) }) {
        SqlResultPlaceholder("Ни один source не вернул успешный табличный результат для сравнения.")
        return
    }
    val diffView = buildSqlConsoleResultDiffView(result)
    if (diffView == null) {
        SqlResultPlaceholder("Diff-режим доступен только для табличных результатов.")
        return
    }
    ResultMutedText("Сравнение строится относительно baseline source ${diffView.baselineSourceName} и использует только уже загруженные строки результата.")
    if (diffView.truncated) {
        ResultMutedText("Хотя бы один source вернул усеченный результат. Diff отражает только видимую часть набора данных.")
    }
    Div({ classes("sql-diff-summary-grid") }) {
        diffView.sourceSummaries.forEach { summary ->
            SqlConsoleDiffSummaryCard(summary = summary, baselineSourceName = diffView.baselineSourceName)
        }
    }
    ResultMutedText(
        when {
            diffView.totalMismatchCount == 0 -> "Различий по source не найдено."
            diffView.mismatchLimitReached ->
                "Показаны первые ${diffView.entries.size} различий из ${diffView.totalMismatchCount}. Полный compare не рисуется, чтобы не раздувать экран."
            else -> "Найдено различий: ${diffView.totalMismatchCount}."
        },
    )
    if (diffView.entries.isEmpty()) {
        SqlResultPlaceholder("Для текущего statement расхождений между source не найдено.")
        return
    }
    Div({ classes("table-responsive") }) {
        Table({ classes("table", "table-sm", "sql-diff-table", "mb-0") }) {
            Thead {
                Tr {
                    Th { Text("Source") }
                    Th { Text("Тип") }
                    Th { Text("Строка") }
                    Th { Text("Колонка") }
                    Th { Text("Baseline") }
                    Th { Text("Source value") }
                    Th { Text("Комментарий") }
                }
            }
            Tbody {
                diffView.entries.forEach { entry ->
                    Tr {
                        Td { Text(entry.sourceName) }
                        Td { Text(translateDiffEntryKind(entry.kind)) }
                        Td { Text(entry.rowNumber?.toString() ?: "—") }
                        Td { Text(entry.columnName ?: "—") }
                        Td { Text(entry.baselineValue.renderDiffValue()) }
                        Td { Text(entry.sourceValue.renderDiffValue()) }
                        Td { Text(entry.message ?: "—") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SqlConsoleDiffSummaryCard(
    summary: SqlConsoleResultDiffSourceSummary,
    baselineSourceName: String,
) {
    Div({
        classes("sql-diff-summary-card", "sql-diff-summary-card-${summary.state.lowercase()}")
    }) {
        Div({ classes("sql-diff-summary-card-head") }) {
            Span({ classes("sql-diff-summary-card-source") }) { Text(summary.sourceName) }
            Span({ classes("sql-diff-summary-card-state") }) { Text(translateDiffSummaryState(summary.state, baselineSourceName)) }
        }
        Div({ classes("sql-diff-summary-card-meta") }) {
            Text("Row count: ${summary.rowCount}")
        }
        Div({ classes("sql-diff-summary-card-meta") }) {
            Text("Mismatch count: ${summary.mismatchCount}")
        }
        summary.errorMessage?.let {
            Div({ classes("sql-diff-summary-card-note") }) { Text(it) }
        }
    }
}

private fun translateDiffSummaryState(
    state: String,
    baselineSourceName: String,
): String =
    when (state) {
        "BASELINE" -> "Baseline ($baselineSourceName)"
        "MATCH" -> "Совпадает"
        "MISMATCH" -> "Есть отличия"
        "FAILED" -> "Source failure"
        else -> state
    }

private fun translateDiffEntryKind(kind: String): String =
    when (kind) {
        "ROW_COUNT" -> "Row count"
        "VALUE_MISMATCH" -> "Value mismatch"
        "MISSING_ROW" -> "Missing row"
        "EXTRA_ROW" -> "Extra row"
        "SOURCE_FAILURE" -> "Source failure"
        else -> kind
    }

private fun String?.renderDiffValue(): String =
    when {
        this == null -> "∅"
        this.isBlank() -> "''"
        else -> this
    }
