package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr
import org.jetbrains.compose.web.dom.Ul

@Composable
internal fun SelectResultPane(
    execution: SqlConsoleExecutionResponse?,
    result: SqlConsoleQueryResult?,
    pageSize: Int,
    selectedShard: String?,
    currentPage: Int,
    onSelectShard: (String) -> Unit,
    onSelectPage: (Int) -> Unit,
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
    Ul({ classes("nav", "nav-tabs", "sql-result-tabs", "mb-3") }) {
        successfulShards.forEach { shard ->
            Li({ classes("nav-item") }) {
                Button(attrs = {
                    classes("nav-link")
                    if (shard.shardName == activeShard.shardName) {
                        classes("active")
                    }
                    attr("type", "button")
                    onClick { onSelectShard(shard.shardName) }
                }) {
                    Text("${shard.shardName} (${shard.rowCount})")
                }
            }
        }
    }
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
    Div({ classes("table-responsive") }) {
        Table({ classes("table", "table-sm", "table-striped", "sql-result-table", "mb-0") }) {
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
                            Td { Text(row[column] ?: "") }
                        }
                    }
                }
            }
        }
    }
    if (totalPages > 1) {
        Div({ classes("sql-pagination-footer") }) {
            Div({ classes("small", "text-secondary") }) {
                Text("Страница $normalizedPage из $totalPages")
            }
            Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                (1..totalPages).forEach { page ->
                    Button(attrs = {
                        classes(
                            "btn",
                            "btn-sm",
                            if (page == normalizedPage) "btn-dark" else "btn-outline-secondary",
                        )
                        attr("type", "button")
                        onClick { onSelectPage(page) }
                    }) {
                        Text(page.toString())
                    }
                }
            }
        }
    }
}
