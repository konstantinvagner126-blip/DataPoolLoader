package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane
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
internal fun SqlConsoleWorkspacePanel(
    state: SqlConsolePageState,
    selectedRecentQuery: String,
    selectedFavoriteQuery: String,
    editorCursorLine: Int,
    scriptOutline: List<SqlScriptOutlineItem>,
    currentOutlineItem: SqlScriptOutlineItem?,
    statementAnalysis: SqlStatementAnalysis,
    runButtonClass: String,
    pendingManualTransaction: Boolean,
    isRunning: Boolean,
    currentExecution: SqlConsoleExecutionResponse?,
    runningClockTick: Int,
    statementResults: List<SqlConsoleStatementResult>,
    exportableResult: SqlConsoleQueryResult?,
    activeExportShard: String?,
    activeOutputTab: String,
    selectedStatementIndex: Int,
    selectedResultShard: String?,
    currentDataPage: Int,
    onRecentSelected: (String) -> Unit,
    onFavoriteSelected: (String) -> Unit,
    onApplyRecent: () -> Unit,
    onApplyFavorite: () -> Unit,
    onRememberFavorite: () -> Unit,
    onRemoveFavorite: () -> Unit,
    onClearRecent: () -> Unit,
    onStrictSafetyToggle: () -> Unit,
    onAutoCommitToggle: (Boolean) -> Unit,
    onInsertFavoriteObject: (SqlConsoleFavoriteObject, String) -> Unit,
    onOpenFavoriteMetadata: (SqlConsoleFavoriteObject) -> Unit,
    onRemoveFavoriteObject: (SqlConsoleFavoriteObject) -> Unit,
    onJumpToLine: (Int) -> Unit,
    onEditorReady: (Any) -> Unit,
    onDraftSqlChange: (String) -> Unit,
    onPageSizeChange: (Int) -> Unit,
    onFormatSql: () -> Unit,
    onRunCurrent: () -> Unit,
    onRunAll: () -> Unit,
    onStop: () -> Unit,
    onCommit: () -> Unit,
    onRollback: () -> Unit,
    onExportCsv: () -> Unit,
    onExportZip: () -> Unit,
    onSelectStatement: (Int) -> Unit,
    onSelectOutputTab: (String) -> Unit,
    onSelectShard: (String?) -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    Div({ classes("panel", "sql-workspace-panel") }) {
        Div({ classes("d-flex", "flex-wrap", "align-items-start", "justify-content-between", "gap-3", "mb-3") }) {
            Div {
                Div({ classes("panel-title", "mb-1") }) { Text("SQL-редактор") }
                Div({ classes("text-secondary", "small") }) {
                    Text("Поддерживается один SQL или SQL-скрипт из нескольких statement-ов. Результат показывается отдельно по каждому statement и source.")
                }
            }
        }

        QueryLibraryBlock(
            state = state,
            selectedRecentQuery = selectedRecentQuery,
            selectedFavoriteQuery = selectedFavoriteQuery,
            onRecentSelected = onRecentSelected,
            onFavoriteSelected = onFavoriteSelected,
            onApplyRecent = onApplyRecent,
            onApplyFavorite = onApplyFavorite,
            onRememberFavorite = onRememberFavorite,
            onRemoveFavorite = onRemoveFavorite,
            onClearRecent = onClearRecent,
            onStrictSafetyToggle = onStrictSafetyToggle,
            onAutoCommitToggle = onAutoCommitToggle,
        )

        SqlFavoriteObjectsBlock(
            favorites = state.favoriteObjects,
            onInsert = { favorite ->
                onInsertFavoriteObject(favorite, favorite.qualifiedName())
            },
            onInsertSelect = { favorite ->
                onInsertFavoriteObject(favorite, buildFavoritePreviewSql(favorite))
            },
            onInsertCount = { favorite ->
                onInsertFavoriteObject(favorite, buildFavoriteCountSql(favorite))
            },
            onOpenMetadata = onOpenFavoriteMetadata,
            onRemove = onRemoveFavoriteObject,
        )

        SqlEditorIdeBlock(
            outlineItems = scriptOutline,
            currentLine = editorCursorLine,
            onJumpToLine = onJumpToLine,
        )

        MonacoEditorPane(
            instanceKey = "compose-sql-console-editor",
            language = "sql",
            value = state.draftSql,
            classNames = listOf("editor-frame", "sql-editor-frame"),
            onEditorReady = { editor -> onEditorReady(editor) },
            onValueChange = onDraftSqlChange,
        )

        SqlConsoleWorkspaceToolbar(
            state = state,
            currentOutlineItem = currentOutlineItem,
            runButtonClass = runButtonClass,
            pendingManualTransaction = pendingManualTransaction,
            isRunning = isRunning,
            exportableResult = exportableResult,
            activeExportShard = activeExportShard,
            onPageSizeChange = onPageSizeChange,
            onFormatSql = onFormatSql,
            onRunCurrent = onRunCurrent,
            onRunAll = onRunAll,
            onStop = onStop,
            onCommit = onCommit,
            onRollback = onRollback,
            onExportCsv = onExportCsv,
            onExportZip = onExportZip,
        )

        CommandGuardrail(analysis = statementAnalysis, strictSafetyEnabled = state.strictSafetyEnabled)
        ExecutionStatusStrip(currentExecution, runningClockTick)

        Div({ classes("sql-output-panel") }) {
            StatementSelectionBlock(
                statementResults = statementResults,
                selectedStatementIndex = selectedStatementIndex,
                onSelectStatement = onSelectStatement,
            )
            QueryOutputPanel(
                execution = currentExecution,
                result = exportableResult,
                pageSize = state.pageSize,
                activeTab = activeOutputTab,
                selectedShard = selectedResultShard,
                currentPage = currentDataPage,
                onSelectTab = onSelectOutputTab,
                onSelectShard = onSelectShard,
                onSelectPage = onSelectPage,
            )
        }
    }
}

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
