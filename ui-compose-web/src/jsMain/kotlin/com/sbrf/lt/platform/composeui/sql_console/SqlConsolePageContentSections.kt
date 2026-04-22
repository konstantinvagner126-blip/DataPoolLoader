package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.runtime.buildRuntimeModeFallbackMessage
import com.sbrf.lt.platform.composeui.foundation.runtime.hasModeFallback
import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import org.w3c.files.File
import org.jetbrains.compose.web.dom.Div

@Composable
internal fun SqlConsolePageContent(
    state: SqlConsolePageState,
    runtimeContext: RuntimeContext?,
    connectionStatusBySource: Map<String, SqlConsoleSourceConnectionStatus>,
    credentialsStatus: CredentialsStatusResponse?,
    credentialsMessage: String?,
    credentialsMessageLevel: String,
    selectedCredentialsFile: File?,
    credentialsUploadInProgress: Boolean,
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
    onCheckConnections: () -> Unit,
    onMaxRowsDraftChange: (String) -> Unit,
    onTimeoutDraftChange: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onToggleSource: (String, Boolean) -> Unit,
    onCredentialsFileSelected: (File?) -> Unit,
    onUploadCredentials: () -> Unit,
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
    state.errorMessage?.let { AlertBanner(it, "warning") }
    state.successMessage?.let { AlertBanner(it, "success") }
    runtimeContext?.takeIf { it.hasModeFallback() }?.let { fallbackContext ->
        AlertBanner(
            buildRuntimeModeFallbackMessage(
                fallbackContext,
                suffix = "SQL-консоль доступна, однако экраны модулей работают по текущему runtime-context.",
            ),
            "warning",
        )
    }

    if (state.loading && state.info == null) {
        LoadingStateCard(title = "SQL-консоль", text = "Конфигурация SQL-консоли загружается.")
        return
    }

    Div({ classes("row", "g-4") }) {
        Div({ classes("col-12", "col-xl-3") }) {
            SqlConsoleSourceSidebar(
                state = state,
                connectionStatusBySource = connectionStatusBySource,
                credentialsStatus = credentialsStatus,
                credentialsMessage = credentialsMessage,
                credentialsMessageLevel = credentialsMessageLevel,
                selectedCredentialsFile = selectedCredentialsFile,
                credentialsUploadInProgress = credentialsUploadInProgress,
                onCheckConnections = onCheckConnections,
                onMaxRowsDraftChange = onMaxRowsDraftChange,
                onTimeoutDraftChange = onTimeoutDraftChange,
                onSaveSettings = onSaveSettings,
                onToggleSource = onToggleSource,
                onCredentialsFileSelected = onCredentialsFileSelected,
                onUploadCredentials = onUploadCredentials,
            )
        }

        Div({ classes("col-12", "col-xl-9") }) {
            SqlConsoleWorkspacePanel(
                state = state,
                selectedRecentQuery = selectedRecentQuery,
                selectedFavoriteQuery = selectedFavoriteQuery,
                editorCursorLine = editorCursorLine,
                scriptOutline = scriptOutline,
                currentOutlineItem = currentOutlineItem,
                statementAnalysis = statementAnalysis,
                runButtonClass = runButtonClass,
                pendingManualTransaction = pendingManualTransaction,
                isRunning = isRunning,
                currentExecution = currentExecution,
                runningClockTick = runningClockTick,
                statementResults = statementResults,
                exportableResult = exportableResult,
                activeExportShard = activeExportShard,
                activeOutputTab = activeOutputTab,
                selectedStatementIndex = selectedStatementIndex,
                selectedResultShard = selectedResultShard,
                currentDataPage = currentDataPage,
                onRecentSelected = onRecentSelected,
                onFavoriteSelected = onFavoriteSelected,
                onApplyRecent = onApplyRecent,
                onApplyFavorite = onApplyFavorite,
                onRememberFavorite = onRememberFavorite,
                onRemoveFavorite = onRemoveFavorite,
                onClearRecent = onClearRecent,
                onStrictSafetyToggle = onStrictSafetyToggle,
                onAutoCommitToggle = onAutoCommitToggle,
                onInsertFavoriteObject = onInsertFavoriteObject,
                onOpenFavoriteMetadata = onOpenFavoriteMetadata,
                onRemoveFavoriteObject = onRemoveFavoriteObject,
                onJumpToLine = onJumpToLine,
                onEditorReady = onEditorReady,
                onDraftSqlChange = onDraftSqlChange,
                onPageSizeChange = onPageSizeChange,
                onFormatSql = onFormatSql,
                onRunCurrent = onRunCurrent,
                onRunAll = onRunAll,
                onStop = onStop,
                onCommit = onCommit,
                onRollback = onRollback,
                onExportCsv = onExportCsv,
                onExportZip = onExportZip,
                onSelectStatement = onSelectStatement,
                onSelectOutputTab = onSelectOutputTab,
                onSelectShard = onSelectShard,
                onSelectPage = onSelectPage,
            )
        }
    }
}
