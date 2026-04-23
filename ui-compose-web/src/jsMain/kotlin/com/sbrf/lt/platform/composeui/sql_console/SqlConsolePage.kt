package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.sql_console.runButtonTone
import org.jetbrains.compose.web.dom.Div

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

    PageScaffold(
        eyebrow = "Load Testing Data Platform",
        title = "SQL-консоль по источникам",
        subtitle = "Проверяй доступность sources, выполняй один SQL или SQL-скрипт по выбранным подключениям и сравнивай результат по каждому statement и source.",
        heroClassNames = listOf("hero-card-compact", "sql-console-hero"),
        heroCopyClassNames = listOf("sql-console-hero-copy"),
        heroHeader = {
            Div({ classes("hero-actions", "mb-3") }) {
                SqlConsoleNavActionButton("На главную", hrefValue = "/")
                SqlConsoleNavActionButton(
                    "Объекты БД",
                    hrefValue = buildSqlConsoleObjectsWorkspaceHref(resolveSqlConsoleWorkspaceId()),
                )
                SqlConsoleNavActionButton("SQL-консоль", active = true)
            }
        },
        heroArt = {
            SqlConsoleHeroArt()
        },
        content = {
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
        },
    )
}
