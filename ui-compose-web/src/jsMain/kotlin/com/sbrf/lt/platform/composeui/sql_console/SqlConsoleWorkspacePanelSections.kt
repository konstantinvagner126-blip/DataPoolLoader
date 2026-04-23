package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleWorkspacePanel(
    state: SqlConsolePageState,
    selectedRecentQuery: String,
    selectedFavoriteQuery: String,
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
            SqlConsoleResultNavigator(
                statementResults = statementResults,
                selectedStatementIndex = selectedStatementIndex,
                result = exportableResult,
                activeTab = activeOutputTab,
                selectedShard = selectedResultShard,
                currentPage = currentDataPage,
                pageSize = state.pageSize,
                onSelectStatement = onSelectStatement,
                onSelectShard = onSelectShard,
                onSelectPage = onSelectPage,
            )
            QueryOutputPanel(
                execution = currentExecution,
                result = exportableResult,
                pageSize = state.pageSize,
                activeTab = activeOutputTab,
                selectedShard = selectedResultShard,
                currentPage = currentDataPage,
                onSelectTab = onSelectOutputTab,
            )
        }
    }
}
