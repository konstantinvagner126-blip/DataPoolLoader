package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Footer
import org.jetbrains.compose.web.dom.Text

@Composable
fun ComposeSqlConsolePage(
    api: SqlConsoleApi = remember { SqlConsoleApiClient() },
) {
    val store = remember(api) { SqlConsoleStore(api) }
    val httpClient = remember { ComposeHttpClient() }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SqlConsolePageState()) }
    var uiState by remember { mutableStateOf(SqlConsolePageUiState()) }
    val currentExecution = state.currentExecution
    val currentResult = currentExecution?.result
    val statementResults = currentResult.statementResultsOrSelf()
    val activeStatementResult = statementResults.getOrNull(uiState.selectedStatementIndex)
    val statementAnalysis = analyzeSqlStatement(state.draftSql)
    val scriptOutline = remember(state.draftSql) { parseSqlScriptOutline(state.draftSql) }
    val currentOutlineItem = scriptOutline.firstOrNull { uiState.editorCursorLine in it.startLine..it.endLine }
        ?: scriptOutline.lastOrNull { uiState.editorCursorLine >= it.startLine }
    val isRunning = currentExecution?.status.equals("RUNNING", ignoreCase = true)
    val pendingManualTransaction = currentExecution?.transactionState == "PENDING_COMMIT"
    val runButtonClass = "btn-${runButtonTone(statementAnalysis, state.strictSafetyEnabled)}"
    val runtimeContext = state.runtimeContext
    val connectionStatusBySource = state.sourceStatuses.associateBy { it.sourceName }
    val exportableResult = activeStatementResult?.toStandaloneQueryResult(currentResult)
    val activeExportShard = activeStatementResult
        ?.takeIf { it.statementType == "RESULT_SET" }
        ?.shardResults
        ?.firstOrNull { it.shardName == uiState.selectedResultShard && it.status.equals("SUCCESS", ignoreCase = true) && it.rows.isNotEmpty() }
        ?.shardName
    val callbacks = sqlConsolePageCallbacks(
        store = store,
        scope = scope,
        httpClient = httpClient,
        currentState = { state },
        setState = { state = it },
        currentUiState = { uiState },
        setUiState = { uiState = it },
        currentOutlineItem = { currentOutlineItem },
        pendingManualTransaction = { pendingManualTransaction },
        isRunning = { isRunning },
        exportableResult = { exportableResult },
        activeExportShard = { activeExportShard },
    )

    SqlConsolePageEffects(
        store = store,
        httpClient = httpClient,
        currentState = { state },
        setState = { state = it },
        currentUiState = { uiState },
        setUiState = { uiState = it },
        currentResult = currentResult,
        statementResults = statementResults,
        isRunning = isRunning,
        currentExecution = currentExecution,
    )
    SqlConsoleMonacoObjectNavigationEffect(
        api = api,
        scope = scope,
        currentState = { state },
        setState = { state = it },
    )

    Div({ classes("container-fluid", "py-4", "compose-home-root", "sql-console-page-root") }) {
        Div({ classes("sql-console-page-shell") }) {
            SqlConsoleToolHeader(
                state = state,
                workspaceId = uiState.workspaceId,
                isRunning = isRunning,
                pendingManualTransaction = pendingManualTransaction,
            )
            SqlConsolePageContent(
                state = state,
                uiState = uiState,
                callbacks = callbacks,
                runtimeContext = runtimeContext,
                connectionStatusBySource = connectionStatusBySource,
                currentOutlineItem = currentOutlineItem,
                statementAnalysis = statementAnalysis,
                runButtonClass = runButtonClass,
                pendingManualTransaction = pendingManualTransaction,
                isRunning = isRunning,
                currentExecution = currentExecution,
                statementResults = statementResults,
                exportableResult = exportableResult,
                activeExportShard = activeExportShard,
            )
        }

        Footer({ classes("footer-note", "text-center", "mt-4") }) {
            Text("Разработано командой MLP")
        }
    }
}
