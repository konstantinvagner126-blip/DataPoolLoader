package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import kotlinx.browser.window
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

@Composable
internal fun SelectResultPane(
    execution: SqlConsoleExecutionResponse?,
    result: SqlConsoleQueryResult?,
    pageSize: Int,
    selectedShard: String?,
    currentPage: Int,
) {
    if (
        RenderExecutionResultPlaceholder(
            execution = execution,
            result = result,
            emptyText = "Пока нет данных для отображения.",
            pendingText = "Выполни запрос, чтобы увидеть данные со всех shard/source.",
            resultPendingLeadText = "Выполняется запрос...",
        )
    ) {
        return
    }
    val readyResult = requireNotNull(result)

    if (readyResult.statementType != "RESULT_SET") {
        SqlResultPlaceholder("Команда ${readyResult.statementKeyword} не возвращает табличные данные. Смотри вкладку «Статусы».")
        return
    }

    val successfulShards = readyResult.shardResults.filter { it.status.equals("SUCCESS", ignoreCase = true) && it.rows.isNotEmpty() }
    if (successfulShards.isEmpty()) {
        SqlResultPlaceholder("Ни один source не вернул данные для отображения.")
        return
    }

    val activeShard = successfulShards.firstOrNull { it.shardName == selectedShard } ?: successfulShards.first()
    val totalPages = maxOf(1, (activeShard.rowCount + pageSize - 1) / pageSize)
    val normalizedPage = currentPage.coerceIn(1, totalPages)
    val startIndex = (normalizedPage - 1) * pageSize
    val endIndexExclusive = minOf(startIndex + pageSize, activeShard.rowCount)
    val visibleRows = activeShard.rows.drop(startIndex).take(pageSize)

    ResultMutedText("Данные показываются отдельно по каждому source. Лимит на source: ${readyResult.maxRowsPerShard}.")
    ResultMutedText(
        buildResultPageSummary(
            shard = activeShard,
            result = readyResult,
            startIndex = startIndex,
            endIndexExclusive = endIndexExclusive,
            normalizedPage = normalizedPage,
            totalPages = totalPages,
        ),
    )
    ResultMutedText("Ячейки можно нажимать: значение копируется в буфер обмена, полный текст доступен по наведению.")
    Div({ classes("table-responsive") }) {
        Table({ classes("table", "table-sm", "sql-result-table", "mb-0") }) {
            Thead {
                Tr {
                    activeShard.columns.forEach { column ->
                        Th { Text(column) }
                    }
                }
            }
            Tbody {
                visibleRows.forEach { row ->
                    Tr {
                        activeShard.columns.forEach { column ->
                            val value = row[column] ?: ""
                            Td(attrs = {
                                classes("sql-result-table-cell")
                                attr("title", if (value.isBlank()) "Пустое значение" else "Клик, чтобы скопировать: $value")
                                attr("tabindex", "0")
                                onClick { copySqlResultCellValue(value) }
                            }) {
                                Div({
                                    classes(
                                        "sql-result-cell-value",
                                        if (value.isBlank()) "sql-result-cell-value-empty" else "sql-result-cell-value-filled",
                                    )
                                }) {
                                    Text(if (value.isBlank()) "∅" else value)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copySqlResultCellValue(value: String) {
    window.navigator.asDynamic().clipboard?.writeText(value)
}
