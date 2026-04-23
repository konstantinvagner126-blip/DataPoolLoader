package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.runtime.buildRuntimeModeFallbackMessage
import com.sbrf.lt.platform.composeui.foundation.runtime.hasModeFallback
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import org.jetbrains.compose.web.dom.Div

@Composable
internal fun SqlConsolePageContent(
    state: SqlConsolePageState,
    uiState: SqlConsolePageUiState,
    callbacks: SqlConsolePageCallbacks,
    runtimeContext: RuntimeContext?,
    connectionStatusBySource: Map<String, SqlConsoleSourceConnectionStatus>,
    currentOutlineItem: SqlScriptOutlineItem?,
    statementAnalysis: SqlStatementAnalysis,
    runButtonClass: String,
    pendingManualTransaction: Boolean,
    isRunning: Boolean,
    currentExecution: SqlConsoleExecutionResponse?,
    statementResults: List<SqlConsoleStatementResult>,
    exportableResult: SqlConsoleQueryResult?,
    activeExportShard: String?,
) {
    state.errorMessage?.let { AlertBanner(it, "warning") }
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
                credentialsStatus = uiState.credentialsStatus,
                credentialsMessage = uiState.credentialsMessage,
                credentialsMessageLevel = uiState.credentialsMessageLevel,
                selectedCredentialsFile = uiState.selectedCredentialsFile,
                credentialsUploadInProgress = uiState.credentialsUploadInProgress,
                onCheckConnections = callbacks.onCheckConnections,
                onMaxRowsDraftChange = callbacks.onMaxRowsDraftChange,
                onTimeoutDraftChange = callbacks.onTimeoutDraftChange,
                onSaveSettings = callbacks.onSaveSettings,
                onToggleSourceGroup = callbacks.onToggleSourceGroup,
                onToggleSource = callbacks.onToggleSource,
                onCredentialsFileSelected = callbacks.onCredentialsFileSelected,
                onUploadCredentials = callbacks.onUploadCredentials,
            )
        }

        Div({ classes("col-12", "col-xl-9") }) {
            SqlConsoleWorkspacePanel(
                state = state,
                editorFocused = uiState.editorFocused,
                selectedSqlText = uiState.selectedSqlText,
                selectedSqlLineCount = uiState.selectedSqlLineCount,
                selectedRecentQuery = uiState.selectedRecentQuery,
                selectedFavoriteQuery = uiState.selectedFavoriteQuery,
                currentOutlineItem = currentOutlineItem,
                statementAnalysis = statementAnalysis,
                runButtonClass = runButtonClass,
                pendingManualTransaction = pendingManualTransaction,
                isRunning = isRunning,
                currentExecution = currentExecution,
                runningClockTick = uiState.runningClockTick,
                statementResults = statementResults,
                exportableResult = exportableResult,
                activeExportShard = activeExportShard,
                activeOutputTab = uiState.activeOutputTab,
                selectedStatementIndex = uiState.selectedStatementIndex,
                selectedResultShard = uiState.selectedResultShard,
                currentDataPage = uiState.currentDataPage,
                onRecentSelected = callbacks.onRecentSelected,
                onFavoriteSelected = callbacks.onFavoriteSelected,
                onApplyRecent = callbacks.onApplyRecent,
                onApplyFavorite = callbacks.onApplyFavorite,
                onRememberFavorite = callbacks.onRememberFavorite,
                onRemoveFavorite = callbacks.onRemoveFavorite,
                onClearRecent = callbacks.onClearRecent,
                onStrictSafetyToggle = callbacks.onStrictSafetyToggle,
                onAutoCommitToggle = callbacks.onAutoCommitToggle,
                onInsertFavoriteObject = callbacks.onInsertFavoriteObject,
                onOpenFavoriteMetadata = callbacks.onOpenFavoriteMetadata,
                onRemoveFavoriteObject = callbacks.onRemoveFavoriteObject,
                onEditorReady = callbacks.onEditorReady,
                onFocusEditor = callbacks.onFocusEditor,
                onDraftSqlChange = callbacks.onDraftSqlChange,
                onPageSizeChange = callbacks.onPageSizeChange,
                onFormatSql = callbacks.onFormatSql,
                onOpenNewTab = callbacks.onOpenNewTab,
                onRunCurrent = callbacks.onRunCurrent,
                onRunSelection = callbacks.onRunSelection,
                onRunAll = callbacks.onRunAll,
                onStop = callbacks.onStop,
                onCommit = callbacks.onCommit,
                onRollback = callbacks.onRollback,
                onExportCsv = callbacks.onExportCsv,
                onExportZip = callbacks.onExportZip,
                onSelectStatement = callbacks.onSelectStatement,
                onSelectOutputTab = callbacks.onSelectOutputTab,
                onSelectShard = callbacks.onSelectShard,
                onSelectPage = callbacks.onSelectPage,
            )
        }
    }
}
