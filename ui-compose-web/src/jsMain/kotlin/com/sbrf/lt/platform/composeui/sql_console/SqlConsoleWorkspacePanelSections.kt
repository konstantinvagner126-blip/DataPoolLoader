package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleWorkspacePanel(
    state: SqlConsolePageState,
    editorFocused: Boolean,
    selectedSqlText: String,
    selectedSqlLineCount: Int,
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
    activeDataView: String,
    selectedStatementIndex: Int,
    selectedResultShard: String?,
    currentDataPage: Int,
    onRecentSelected: (String) -> Unit,
    onFavoriteSelected: (String) -> Unit,
    onApplyRecent: () -> Unit,
    onApplyFavorite: () -> Unit,
    onOpenExecutionHistory: () -> Unit,
    onRememberFavorite: () -> Unit,
    onRemoveFavorite: () -> Unit,
    onClearRecent: () -> Unit,
    onStrictSafetyToggle: () -> Unit,
    onAutoCommitToggle: (Boolean) -> Unit,
    onInsertFavoriteObject: (SqlConsoleFavoriteObject, String) -> Unit,
    onOpenFavoriteMetadata: (SqlConsoleFavoriteObject) -> Unit,
    onRemoveFavoriteObject: (SqlConsoleFavoriteObject) -> Unit,
    onEditorReady: (Any) -> Unit,
    onFocusEditor: () -> Unit,
    onDraftSqlChange: (String) -> Unit,
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
    onSelectStatement: (Int) -> Unit,
    onSelectOutputTab: (String) -> Unit,
    onSelectDataView: (String) -> Unit,
    onSelectShard: (String?) -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    Div({ classes("panel", "sql-shell-pane", "sql-workspace-panel") }) {
        Div({ classes("sql-shell-pane-head", "sql-workspace-pane-head", "mb-3") }) {
            Div {
                Div({ classes("panel-title", "mb-1") }) { Text("SQL-редактор") }
                Div({ classes("text-secondary", "small", "sql-shell-pane-note") }) {
                    Text("Поддерживается один SQL или SQL-скрипт из нескольких statement-ов. Результат показывается отдельно по каждому statement и source.")
                }
            }
        }

        Div({ classes("sql-workspace-control-strip") }) {
            SqlConsoleQuerySettingsBlock(
                state = state,
                onStrictSafetyToggle = onStrictSafetyToggle,
                onAutoCommitToggle = onAutoCommitToggle,
            )
        }

        SqlConsoleWorkspaceToolbar(
            state = state,
            selectedSqlText = selectedSqlText,
            selectedSqlLineCount = selectedSqlLineCount,
            currentOutlineItem = currentOutlineItem,
            runButtonClass = runButtonClass,
            pendingManualTransaction = pendingManualTransaction,
            isRunning = isRunning,
            onPageSizeChange = onPageSizeChange,
            onFormatSql = onFormatSql,
            onOpenNewTab = onOpenNewTab,
            onExplainCurrent = onExplainCurrent,
            onExplainAnalyzeCurrent = onExplainAnalyzeCurrent,
            onExplainSelection = onExplainSelection,
            onExplainAnalyzeSelection = onExplainAnalyzeSelection,
            onRunCurrent = onRunCurrent,
            onRunSelection = onRunSelection,
            onRunAll = onRunAll,
            onStop = onStop,
            onCommit = onCommit,
            onRollback = onRollback,
        )

        MonacoEditorPane(
            instanceKey = "compose-sql-console-editor",
            language = "sql",
            value = state.draftSql,
            glyphMargin = true,
            sqlObjectNavigation = true,
            classNames = listOf("editor-frame", "sql-editor-frame"),
            onEditorReady = { editor -> onEditorReady(editor) },
            onValueChange = onDraftSqlChange,
        )

        Div({ classes("sql-safety-strip-stack") }) {
            CommandGuardrail(analysis = statementAnalysis, strictSafetyEnabled = state.strictSafetyEnabled)
            ExecutionStatusStrip(currentExecution, runningClockTick)
        }

        Div({ classes("sql-output-panel") }) {
            SqlConsoleResultNavigator(
                statementResults = statementResults,
                selectedStatementIndex = selectedStatementIndex,
                result = exportableResult,
                activeTab = activeOutputTab,
                activeDataView = activeDataView,
                selectedShard = selectedResultShard,
                currentPage = currentDataPage,
                pageSize = state.pageSize,
                activeExportShard = activeExportShard,
                onSelectStatement = onSelectStatement,
                onSelectDataView = onSelectDataView,
                onSelectShard = onSelectShard,
                onSelectPage = onSelectPage,
                onExportCsv = onExportCsv,
                onExportZip = onExportZip,
            )
            QueryOutputPanel(
                execution = currentExecution,
                result = exportableResult,
                pageSize = state.pageSize,
                activeTab = activeOutputTab,
                activeDataView = activeDataView,
                selectedShard = selectedResultShard,
                currentPage = currentDataPage,
                onSelectTab = onSelectOutputTab,
            )
        }

        SqlConsoleWorkingStatusBar(
            state = state,
            execution = currentExecution,
            activeOutputTab = activeOutputTab,
            activeDataView = activeDataView,
            selectedStatementIndex = selectedStatementIndex,
            statementResults = statementResults,
            selectedShard = selectedResultShard,
            currentPage = currentDataPage,
        )

        QueryLibraryBlock(
            state = state,
            selectedRecentQuery = selectedRecentQuery,
            selectedFavoriteQuery = selectedFavoriteQuery,
            onRecentSelected = onRecentSelected,
            onFavoriteSelected = onFavoriteSelected,
            onApplyRecent = onApplyRecent,
            onApplyFavorite = onApplyFavorite,
            onOpenExecutionHistory = onOpenExecutionHistory,
            onRememberFavorite = onRememberFavorite,
            onRemoveFavorite = onRemoveFavorite,
            onClearRecent = onClearRecent,
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

        SqlConsoleShortcutPanel(
            editorFocused = editorFocused,
            hasSelectedSql = selectedSqlText.isNotBlank(),
            onFocusEditor = onFocusEditor,
        )
    }
}
