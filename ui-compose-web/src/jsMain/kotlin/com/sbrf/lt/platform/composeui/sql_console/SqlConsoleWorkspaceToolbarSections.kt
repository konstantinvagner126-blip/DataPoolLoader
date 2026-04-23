package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleWorkspaceToolbar(
    state: SqlConsolePageState,
    selectedSqlText: String,
    selectedSqlLineCount: Int,
    currentOutlineItem: SqlScriptOutlineItem?,
    runButtonClass: String,
    pendingManualTransaction: Boolean,
    isRunning: Boolean,
    exportableResult: SqlConsoleQueryResult?,
    activeExportShard: String?,
    onPageSizeChange: (Int) -> Unit,
    onFormatSql: () -> Unit,
    onOpenNewTab: () -> Unit,
    onExplainCurrent: () -> Unit,
    onExplainAnalyzeCurrent: () -> Unit,
    onExplainSelection: () -> Unit,
    onExplainAnalyzeSelection: () -> Unit,
    onRunCurrent: () -> Unit,
    onRunSelection: () -> Unit,
    onRunAll: () -> Unit,
    onStop: () -> Unit,
    onCommit: () -> Unit,
    onRollback: () -> Unit,
    onExportCsv: () -> Unit,
    onExportZip: () -> Unit,
) {
    Div({ classes("sql-toolbar") }) {
        Div({ classes("sql-toolbar-meta") }) {
            Label(attrs = {
                classes("small", "text-secondary", "mb-0")
                attr("for", "composeSqlPageSize")
            }) { Text("Строк на странице") }
            Select(attrs = {
                id("composeSqlPageSize")
                classes("form-select", "form-select-sm", "sql-page-size-select")
                onChange {
                    onPageSizeChange(it.value?.toIntOrNull() ?: 50)
                }
            }) {
                listOf(25, 50, 100).forEach { pageSize ->
                    Option(value = pageSize.toString(), attrs = {
                        if (state.pageSize == pageSize) {
                            selected()
                        }
                    }) {
                        Text(pageSize.toString())
                    }
                }
            }
        }
        Div({ classes("sql-toolbar-action-groups") }) {
            SqlToolbarActionGroup("Подготовка") {
                SqlToolbarActionButton(
                    title = "Форматировать SQL",
                    icon = "≣",
                    toneClass = "btn-outline-dark",
                    onClick = onFormatSql,
                )
                SqlToolbarActionButton(
                    title = "Открыть новую вкладку консоли",
                    icon = "⊞",
                    toneClass = "btn-outline-dark",
                    onClick = onOpenNewTab,
                )
            }
            SqlToolbarActionGroup(
                title = "EXPLAIN",
                note = buildSqlExplainScopeSummary(
                    currentOutlineItem = currentOutlineItem,
                    selectedSqlText = selectedSqlText,
                    selectedSqlLineCount = selectedSqlLineCount,
                ),
            ) {
                SqlToolbarActionButton(
                    title = "EXPLAIN текущий statement",
                    icon = "↦E",
                    toneClass = "btn-outline-dark",
                    buttonDisabled = (
                        state.actionInProgress == "explain-current-query" ||
                            state.info?.configured != true ||
                            pendingManualTransaction ||
                            currentOutlineItem == null
                        ),
                    onClick = onExplainCurrent,
                )
                SqlToolbarActionButton(
                    title = "EXPLAIN ANALYZE текущий statement",
                    icon = "↦A",
                    toneClass = "btn-outline-warning",
                    buttonDisabled = (
                        state.actionInProgress == "explain-analyze-current-query" ||
                            state.info?.configured != true ||
                            pendingManualTransaction ||
                            currentOutlineItem == null
                        ),
                    onClick = onExplainAnalyzeCurrent,
                )
                SqlToolbarActionButton(
                    title = "EXPLAIN выделение",
                    icon = "▣E",
                    toneClass = "btn-outline-dark",
                    buttonDisabled = (
                        state.actionInProgress == "explain-selection-query" ||
                            state.info?.configured != true ||
                            pendingManualTransaction ||
                            selectedSqlText.isBlank()
                        ),
                    onClick = onExplainSelection,
                )
                SqlToolbarActionButton(
                    title = "EXPLAIN ANALYZE выделение",
                    icon = "▣A",
                    toneClass = "btn-outline-warning",
                    buttonDisabled = (
                        state.actionInProgress == "explain-analyze-selection-query" ||
                            state.info?.configured != true ||
                            pendingManualTransaction ||
                            selectedSqlText.isBlank()
                        ),
                    onClick = onExplainAnalyzeSelection,
                )
            }
            SqlToolbarActionGroup(
                title = "Выполнение",
                note = buildSqlRunScopeSummary(
                    currentOutlineItem = currentOutlineItem,
                    selectedSqlText = selectedSqlText,
                    selectedSqlLineCount = selectedSqlLineCount,
                ),
            ) {
                SqlToolbarActionButton(
                    title = "Выполнить текущий statement",
                    icon = "↦",
                    toneClass = "btn-outline-dark",
                    buttonDisabled = (
                        state.actionInProgress == "run-current-query" ||
                            state.info?.configured != true ||
                            pendingManualTransaction ||
                            currentOutlineItem == null
                        ),
                    onClick = onRunCurrent,
                )
                SqlToolbarActionButton(
                    title = "Выполнить выделение",
                    icon = "▣",
                    toneClass = "btn-outline-dark",
                    buttonDisabled = (
                        state.actionInProgress == "run-selection-query" ||
                            state.info?.configured != true ||
                            pendingManualTransaction ||
                            selectedSqlText.isBlank()
                        ),
                    onClick = onRunSelection,
                )
                SqlToolbarActionButton(
                    title = "Выполнить весь script",
                    icon = "≫",
                    toneClass = runButtonClass,
                    buttonDisabled = state.actionInProgress == "run-query" || state.info?.configured != true || pendingManualTransaction,
                    extraClasses = arrayOf("sql-toolbar-primary-action"),
                    onClick = onRunAll,
                )
                SqlToolbarActionButton(
                    title = "Остановить выполнение",
                    icon = "■",
                    toneClass = "btn-danger",
                    buttonDisabled = !isRunning || state.actionInProgress == "cancel-query",
                    onClick = onStop,
                )
            }
            SqlToolbarActionGroup("Транзакция") {
                SqlToolbarActionButton(
                    title = "Commit",
                    icon = "✓",
                    toneClass = "btn-success",
                    buttonDisabled = !pendingManualTransaction || state.actionInProgress == "commit-query",
                    onClick = onCommit,
                )
                SqlToolbarActionButton(
                    title = "Rollback",
                    icon = "↶",
                    toneClass = "btn-outline-danger",
                    buttonDisabled = !pendingManualTransaction || state.actionInProgress == "rollback-query",
                    onClick = onRollback,
                )
            }
            SqlToolbarActionGroup("Экспорт") {
                SqlToolbarActionButton(
                    title = "Скачать CSV",
                    icon = "▦",
                    toneClass = "btn-outline-secondary",
                    buttonDisabled = activeExportShard == null,
                    onClick = onExportCsv,
                )
                SqlToolbarActionButton(
                    title = "Скачать ZIP",
                    icon = "⇩",
                    toneClass = "btn-outline-secondary",
                    buttonDisabled = exportableResult?.statementType != "RESULT_SET",
                    onClick = onExportZip,
                )
            }
        }
    }
}

@Composable
private fun SqlToolbarActionGroup(
    title: String,
    note: String? = null,
    content: @Composable () -> Unit,
) {
    Div({ classes("sql-toolbar-action-group") }) {
        Div({ classes("sql-toolbar-action-group-head") }) {
            Span({ classes("sql-toolbar-action-group-title") }) { Text(title) }
            note?.let {
                Span({ classes("sql-toolbar-action-group-note") }) { Text(it) }
            }
        }
        Div({ classes("sql-toolbar-action-group-buttons") }) {
            content()
        }
    }
}

private fun buildSqlRunScopeSummary(
    currentOutlineItem: SqlScriptOutlineItem?,
    selectedSqlText: String,
    selectedSqlLineCount: Int,
): String =
    when {
        selectedSqlText.isNotBlank() ->
            if (selectedSqlLineCount > 1) "Выделение: $selectedSqlLineCount строк" else "Выделение: 1 строка"
        currentOutlineItem != null ->
            "Под курсором: ${currentOutlineItem.keyword}"
        else -> "Доступен только весь script"
    }

@Composable
internal fun SqlToolbarActionButton(
    title: String,
    icon: String,
    toneClass: String,
    buttonDisabled: Boolean = false,
    extraClasses: Array<String> = emptyArray(),
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", toneClass, "sql-toolbar-icon-button", *extraClasses)
        attr("type", "button")
        attr("title", title)
        attr("aria-label", title)
        if (buttonDisabled) {
            disabled()
        }
        onClick { onClick() }
    }) {
        Span({ classes("sql-toolbar-icon") }) { Text(icon) }
    }
}
