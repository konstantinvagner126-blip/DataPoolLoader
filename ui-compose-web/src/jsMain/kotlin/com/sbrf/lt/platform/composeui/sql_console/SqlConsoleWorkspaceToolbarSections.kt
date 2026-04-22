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
    currentOutlineItem: SqlScriptOutlineItem?,
    runButtonClass: String,
    pendingManualTransaction: Boolean,
    isRunning: Boolean,
    exportableResult: SqlConsoleQueryResult?,
    activeExportShard: String?,
    onPageSizeChange: (Int) -> Unit,
    onFormatSql: () -> Unit,
    onRunCurrent: () -> Unit,
    onRunAll: () -> Unit,
    onStop: () -> Unit,
    onCommit: () -> Unit,
    onRollback: () -> Unit,
    onExportCsv: () -> Unit,
    onExportZip: () -> Unit,
) {
    Div({ classes("sql-toolbar") }) {
        Div({ classes("d-flex", "flex-wrap", "align-items-center", "gap-2") }) {
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
        Div({ classes("d-flex", "flex-wrap", "align-items-center", "gap-2") }) {
            SqlToolbarActionButton(
                toneClass = "btn-outline-dark",
                onClick = onFormatSql,
            ) {
                Text("Форматировать")
            }
            SqlToolbarActionButton(
                toneClass = "btn-outline-dark",
                buttonDisabled = (
                    state.actionInProgress == "run-current-query" ||
                        state.info?.configured != true ||
                        pendingManualTransaction ||
                        currentOutlineItem == null
                    ),
                onClick = onRunCurrent,
            ) {
                Text("Текущий")
            }
            SqlToolbarActionButton(
                toneClass = runButtonClass,
                buttonDisabled = state.actionInProgress == "run-query" || state.info?.configured != true || pendingManualTransaction,
                extraClasses = arrayOf("sql-action-button", "sql-action-button-run"),
                onClick = onRunAll,
            ) {
                Span({ classes("sql-action-icon", "sql-action-icon-play") })
            }
            SqlToolbarActionButton(
                toneClass = "btn-danger",
                buttonDisabled = !isRunning || state.actionInProgress == "cancel-query",
                extraClasses = arrayOf("sql-action-button", "sql-action-button-stop"),
                onClick = onStop,
            ) {
                Span({ classes("sql-action-icon", "sql-action-icon-stop") })
            }
            SqlToolbarActionButton(
                toneClass = "btn-success",
                buttonDisabled = !pendingManualTransaction || state.actionInProgress == "commit-query",
                onClick = onCommit,
            ) {
                Text("Commit")
            }
            SqlToolbarActionButton(
                toneClass = "btn-outline-danger",
                buttonDisabled = !pendingManualTransaction || state.actionInProgress == "rollback-query",
                onClick = onRollback,
            ) {
                Text("Rollback")
            }
            SqlToolbarActionButton(
                toneClass = "btn-outline-secondary",
                buttonDisabled = activeExportShard == null,
                onClick = onExportCsv,
            ) {
                Text("Скачать CSV")
            }
            SqlToolbarActionButton(
                toneClass = "btn-outline-secondary",
                buttonDisabled = exportableResult?.statementType != "RESULT_SET",
                onClick = onExportZip,
            ) {
                Text("Скачать ZIP")
            }
        }
    }
}

@Composable
internal fun SqlToolbarActionButton(
    toneClass: String,
    buttonDisabled: Boolean = false,
    extraClasses: Array<String> = emptyArray(),
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Button(attrs = {
        classes("btn", toneClass, *extraClasses)
        attr("type", "button")
        if (buttonDisabled) {
            disabled()
        }
        onClick { onClick() }
    }) {
        content()
    }
}
