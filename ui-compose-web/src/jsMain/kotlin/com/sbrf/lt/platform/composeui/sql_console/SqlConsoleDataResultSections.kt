package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import kotlinx.browser.window
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Col
import org.jetbrains.compose.web.dom.Colgroup
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
internal fun SelectResultPane(
    execution: SqlConsoleExecutionResponse?,
    result: SqlConsoleQueryResult?,
    pageSize: Int,
    activeDataView: String,
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

    if (activeDataView == "diff") {
        SqlConsoleDiffResultPane(result = readyResult)
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
    val gridKey = remember(activeShard.shardName, activeShard.columns.joinToString("\u0001")) {
        activeShard.shardName + "|" + activeShard.columns.joinToString("\u0001")
    }
    var wrapCellContent by remember(gridKey) { mutableStateOf(false) }
    var selectedCell by remember(gridKey) { mutableStateOf<SqlResultGridSelection?>(null) }
    var columnWidths by remember(gridKey) {
        mutableStateOf(
            buildSqlResultGridInitialColumnWidths(
                columns = activeShard.columns,
                rows = activeShard.rows,
            ),
        )
    }
    val selectedColumnName = selectedCell?.columnName
    val selectedRow = selectedCell
        ?.takeIf { it.absoluteRowIndex in startIndex until endIndexExclusive }
        ?.let { visibleRows.getOrNull(it.absoluteRowIndex - startIndex) }
    val selectedCellOnCurrentPage = selectedCell != null && selectedRow != null && selectedColumnName != null

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
    ResultMutedText("Grid поддерживает wrap/nowrap, resize/autosize колонок и copy по ячейке, строке и колонке.")
    Div({ classes("sql-result-grid-toolbar") }) {
        Div({ classes("sql-result-grid-toolbar-actions") }) {
            SqlResultGridToolbarButton(
                label = if (wrapCellContent) "Nowrap" else "Wrap",
                title = if (wrapCellContent) "Отключить перенос строк в ячейках" else "Включить перенос строк в ячейках",
            ) {
                wrapCellContent = !wrapCellContent
            }
            SqlResultGridToolbarButton(
                label = "Autosize",
                title = "Подобрать ширину колонок по содержимому",
            ) {
                columnWidths = buildSqlResultGridInitialColumnWidths(
                    columns = activeShard.columns,
                    rows = activeShard.rows,
                )
            }
            SqlResultGridToolbarButton(
                label = "Copy cell",
                title = "Скопировать активную ячейку",
                enabled = selectedCellOnCurrentPage,
            ) {
                val selectedValue = selectedRow?.get(selectedColumnName).orEmpty()
                copySqlResultToClipboard(buildSqlResultGridCellCopyValue(selectedValue))
            }
            SqlResultGridToolbarButton(
                label = "Copy row",
                title = "Скопировать активную строку как TSV",
                enabled = selectedRow != null,
            ) {
                selectedRow?.let { row ->
                    copySqlResultToClipboard(buildSqlResultGridRowCopyValue(activeShard.columns, row))
                }
            }
            SqlResultGridToolbarButton(
                label = "Copy column",
                title = "Скопировать активную колонку",
                enabled = selectedColumnName != null,
            ) {
                selectedColumnName?.let { columnName ->
                    copySqlResultToClipboard(buildSqlResultGridColumnCopyValue(columnName, activeShard.rows))
                }
            }
        }
        Div({ classes("sql-result-grid-toolbar-note") }) {
            Text(
                if (selectedCell != null && !selectedCellOnCurrentPage) {
                    "Активная ячейка находится вне текущей страницы результата. Выбери ячейку на текущей странице или используй copy column."
                } else selectedCell?.let { selection ->
                    val cellValue = buildSqlResultGridCellCopyValue(selectedRow?.get(selection.columnName))
                    "Активная ячейка: строка ${selection.absoluteRowIndex + 1}, колонка ${selection.columnName}${if (cellValue.isNotBlank()) " • ${cellValue.take(48)}" else " • ∅"}"
                } ?: "Активируй ячейку или колонку, чтобы выполнять copy-действия и видеть фокус grid."
            )
        }
    }
    Div({ classes("table-responsive") }) {
        Table({
            classes("table", "table-sm", "sql-result-table", "sql-result-grid-table", "mb-0")
            if (wrapCellContent) {
                classes("sql-result-grid-wrap")
            } else {
                classes("sql-result-grid-nowrap")
            }
        }) {
            Colgroup {
                Col({
                    attr("style", "width:64px;min-width:64px;max-width:64px;")
                })
                activeShard.columns.forEach { column ->
                    val width = columnWidths[column] ?: SQL_RESULT_GRID_MIN_WIDTH_PX
                    Col({
                        attr("style", "width:${width}px;min-width:${width}px;")
                    })
                }
            }
            Thead {
                Tr {
                    Th({ classes("sql-result-grid-rowhead") }) { Text("#") }
                    activeShard.columns.forEach { column ->
                        val width = columnWidths[column] ?: SQL_RESULT_GRID_MIN_WIDTH_PX
                        Th({
                            classes("sql-result-grid-header")
                            if (selectedColumnName == column) {
                                classes("sql-result-grid-column-active")
                            }
                            attr("style", "width:${width}px;min-width:${width}px;")
                        }) {
                            Div({ classes("sql-result-grid-header-body") }) {
                                Div({ classes("sql-result-grid-header-name-wrap") }) {
                                    Span({ classes("sql-result-grid-header-name") }) { Text(column) }
                                }
                                Div({ classes("sql-result-grid-header-actions") }) {
                                    SqlResultGridIconButton("⧉", "Скопировать колонку $column") {
                                        selectedCell = selectedCell?.copy(columnName = column)
                                            ?: SqlResultGridSelection(startIndex, column)
                                        copySqlResultToClipboard(buildSqlResultGridColumnCopyValue(column, activeShard.rows))
                                    }
                                    SqlResultGridIconButton("↔", "Подобрать ширину колонки $column") {
                                        columnWidths = columnWidths + (
                                            column to calculateSqlResultGridColumnWidthPx(
                                                header = column,
                                                values = activeShard.rows.asSequence().map { row -> row[column].orEmpty() },
                                            )
                                        )
                                    }
                                    SqlResultGridIconButton("−", "Сузить колонку $column") {
                                        columnWidths = columnWidths + (column to shrinkSqlResultGridColumnWidth(width))
                                    }
                                    SqlResultGridIconButton("+", "Расширить колонку $column") {
                                        columnWidths = columnWidths + (column to growSqlResultGridColumnWidth(width))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Tbody {
                visibleRows.forEachIndexed { rowOffset, row ->
                    val absoluteRowIndex = startIndex + rowOffset
                    val isSelectedRow = selectedCell?.absoluteRowIndex == absoluteRowIndex
                    Tr({
                        if (isSelectedRow) {
                            classes("sql-result-grid-row-active")
                        }
                    }) {
                        Td({ classes("sql-result-grid-rowhead-cell") }) {
                            Div({ classes("sql-result-grid-rowhead-body") }) {
                                SqlResultGridIconButton("⧉", "Скопировать строку ${absoluteRowIndex + 1}") {
                                    activeShard.columns.firstOrNull()?.let { firstColumn ->
                                        selectedCell = selectedCell ?: SqlResultGridSelection(absoluteRowIndex, firstColumn)
                                    }
                                    copySqlResultToClipboard(buildSqlResultGridRowCopyValue(activeShard.columns, row))
                                }
                                Span({ classes("sql-result-grid-row-index") }) {
                                    Text((absoluteRowIndex + 1).toString())
                                }
                            }
                        }
                        activeShard.columns.forEach { column ->
                            val value = row[column] ?: ""
                            val isActiveCell = selectedCell?.absoluteRowIndex == absoluteRowIndex &&
                                selectedCell?.columnName == column
                            Td(attrs = {
                                classes("sql-result-table-cell", "sql-result-grid-cell")
                                if (selectedColumnName == column) {
                                    classes("sql-result-grid-column-active")
                                }
                                if (isActiveCell) {
                                    classes("sql-result-grid-cell-active")
                                }
                                attr("title", if (value.isBlank()) "Пустое значение" else "Клик, чтобы активировать и скопировать: $value")
                                attr("tabindex", "0")
                                onClick {
                                    selectedCell = SqlResultGridSelection(
                                        absoluteRowIndex = absoluteRowIndex,
                                        columnName = column,
                                    )
                                    copySqlResultToClipboard(buildSqlResultGridCellCopyValue(value))
                                }
                            }) {
                                Div({
                                    classes(
                                        "sql-result-cell-value",
                                        "sql-result-grid-cell-value",
                                        if (value.isBlank()) "sql-result-cell-value-empty" else "sql-result-cell-value-filled",
                                        if (wrapCellContent) "sql-result-grid-cell-wrap" else "sql-result-grid-cell-nowrap",
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

@Composable
private fun SqlResultGridToolbarButton(
    label: String,
    title: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("sql-result-grid-toolbar-button")
        attr("type", "button")
        attr("title", title)
        if (!enabled) {
            disabled()
        }
        onClick { onClick() }
    }) {
        Text(label)
    }
}

@Composable
private fun SqlResultGridIconButton(
    label: String,
    title: String,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("sql-result-grid-icon-button")
        attr("type", "button")
        attr("title", title)
        onClick { onClick() }
    }) {
        Text(label)
    }
}

private fun copySqlResultToClipboard(value: String) {
    window.navigator.asDynamic().clipboard?.writeText(value)
}
