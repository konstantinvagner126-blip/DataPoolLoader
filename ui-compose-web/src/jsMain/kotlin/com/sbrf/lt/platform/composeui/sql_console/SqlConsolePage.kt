package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.component.MonacoEditorPane
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.format.formatDurationMillis
import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.foundation.http.loadCredentialsStatus
import com.sbrf.lt.platform.composeui.foundation.http.uploadCredentialsFile
import com.sbrf.lt.platform.composeui.foundation.runtime.buildRuntimeModeFallbackMessage
import com.sbrf.lt.platform.composeui.foundation.runtime.hasModeFallback
import com.sbrf.lt.platform.composeui.foundation.updates.PollingEffect
import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.sql_console.buildConnectionCheckStatusText
import com.sbrf.lt.platform.composeui.sql_console.buildConsoleInfoText
import com.sbrf.lt.platform.composeui.sql_console.buildCredentialsStatusText
import com.sbrf.lt.platform.composeui.sql_console.runButtonTone
import com.sbrf.lt.platform.composeui.sql_console.sourceStatusSuffix
import com.sbrf.lt.platform.composeui.sql_console.sourceStatusTone
import com.sbrf.lt.platform.composeui.sql_console.translateSourceStatus
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr
import org.jetbrains.compose.web.dom.Ul

@Composable
fun ComposeSqlConsolePage(
    api: SqlConsoleApi = remember { SqlConsoleApiClient() },
) {
    val store = remember(api) { SqlConsoleStore(api) }
    val httpClient = remember { ComposeHttpClient() }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SqlConsolePageState()) }
    var editorInstance by remember { mutableStateOf<dynamic>(null) }
    var editorCursorLine by remember { mutableStateOf(1) }
    var selectedRecentQuery by remember { mutableStateOf("") }
    var selectedFavoriteQuery by remember { mutableStateOf("") }
    var credentialsStatus by remember { mutableStateOf<CredentialsStatusResponse?>(null) }
    var selectedCredentialsFile by remember { mutableStateOf<File?>(null) }
    var credentialsUploadInProgress by remember { mutableStateOf(false) }
    var credentialsMessage by remember { mutableStateOf<String?>(null) }
    var credentialsMessageLevel by remember { mutableStateOf("success") }
    var activeOutputTab by remember { mutableStateOf("data") }
    var selectedStatementIndex by remember { mutableStateOf(0) }
    var selectedResultShard by remember { mutableStateOf<String?>(null) }
    var currentDataPage by remember { mutableStateOf(1) }
    var runningClockTick by remember { mutableStateOf(0) }
    val currentExecution = state.currentExecution
    val currentResult = currentExecution?.result
    val statementResults = currentResult.statementResultsOrSelf()
    val activeStatementResult = statementResults.getOrNull(selectedStatementIndex)
    val statementAnalysis = analyzeSqlStatement(state.draftSql)
    val scriptOutline = remember(state.draftSql) { parseSqlScriptOutline(state.draftSql) }
    val currentOutlineItem = scriptOutline.firstOrNull { editorCursorLine in it.startLine..it.endLine }
        ?: scriptOutline.lastOrNull { editorCursorLine >= it.startLine }
    val isRunning = currentExecution?.status.equals("RUNNING", ignoreCase = true)
    val pendingManualTransaction = currentExecution?.transactionState == "PENDING_COMMIT"
    val runButtonClass = "btn-${runButtonTone(statementAnalysis, state.strictSafetyEnabled)}"
    val runtimeContext = state.runtimeContext
    val connectionStatusBySource = state.connectionCheck?.sourceResults?.associateBy { it.sourceName }.orEmpty()
    val exportableResult = activeStatementResult?.toStandaloneQueryResult(currentResult)
    val activeExportShard = activeStatementResult
        ?.takeIf { it.statementType == "RESULT_SET" }
        ?.shardResults
        ?.firstOrNull { it.shardName == selectedResultShard && it.status.equals("SUCCESS", ignoreCase = true) && it.rows.isNotEmpty() }
        ?.shardName
    var runAllAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var runCurrentAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var formatSqlAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var stopAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(store) {
        state = store.startLoading(state)
        state = store.load()
        credentialsStatus = loadCredentialsStatus(httpClient)
    }

    LaunchedEffect(
        currentResult?.statementType,
        currentResult?.statementKeyword,
        currentResult?.statementResults?.joinToString("\u0001") { it.statementKeyword + ":" + it.shardResults.joinToString(",") { shard -> shard.shardName } },
        currentResult?.shardResults?.joinToString("\u0001") { it.shardName },
    ) {
        if (currentResult == null) {
            activeOutputTab = "data"
            selectedStatementIndex = 0
            selectedResultShard = null
            currentDataPage = 1
        } else {
            val normalizedStatementIndex = selectedStatementIndex.coerceIn(0, statementResults.lastIndex.coerceAtLeast(0))
            if (selectedStatementIndex != normalizedStatementIndex) {
                selectedStatementIndex = normalizedStatementIndex
            }
            val resultForDisplay = statementResults.getOrNull(normalizedStatementIndex)
            if (resultForDisplay?.statementType == "RESULT_SET") {
                activeOutputTab = "data"
                val successfulShards = resultForDisplay.shardResults.filter { it.status.equals("SUCCESS", ignoreCase = true) }
                if (selectedResultShard !in successfulShards.map { it.shardName }) {
                    selectedResultShard = successfulShards.firstOrNull()?.shardName
                    currentDataPage = 1
                }
            } else {
                activeOutputTab = "status"
                selectedResultShard = null
                currentDataPage = 1
            }
        }
    }

    LaunchedEffect(
        state.draftSql,
        state.selectedSourceNames.joinToString("\u0001"),
        state.pageSize,
        state.strictSafetyEnabled,
        state.recentQueries.joinToString("\u0001"),
        state.favoriteQueries.joinToString("\u0001"),
        state.favoriteObjects.joinToString("\u0001") { favorite ->
            favorite.sourceName + "|" + favorite.schemaName + "|" + favorite.objectName + "|" + favorite.objectType + "|" + (favorite.tableName ?: "")
        },
    ) {
        if (state.loading || state.info == null) {
            return@LaunchedEffect
        }
        delay(500)
        state = store.persistState(state)
    }

    PollingEffect(
        enabled = isRunning,
        intervalMs = 2000,
        onTick = {
            state = store.refreshExecution(state)
        },
    )

    LaunchedEffect(isRunning, currentExecution?.startedAt) {
        if (!isRunning) {
            runningClockTick = 0
            return@LaunchedEffect
        }
        while (true) {
            runningClockTick += 1
            delay(1000)
        }
    }

    runAllAction = {
        if (state.actionInProgress != "run-query" && state.info?.configured == true && !pendingManualTransaction) {
            scope.launch {
                state = store.beginAction(state, "run-query")
                state = store.startQuery(state)
            }
        }
    }
    runCurrentAction = {
        val statementSql = currentOutlineItem?.sql?.trim().orEmpty()
        if (
            statementSql.isNotBlank() &&
            state.actionInProgress != "run-current-query" &&
            state.info?.configured == true &&
            !pendingManualTransaction
        ) {
            scope.launch {
                state = store.beginAction(state, "run-current-query")
                state = store.startQuery(
                    current = state,
                    sqlOverride = statementSql,
                    successMessage = "Текущий statement запущен.",
                )
            }
        }
    }
    formatSqlAction = {
        val formattedSql = formatSqlScript(state.draftSql)
        if (formattedSql != state.draftSql) {
            state = store.updateDraftSql(
                state.copy(errorMessage = null, successMessage = "SQL отформатирован."),
                formattedSql,
            )
        }
    }
    stopAction = {
        if (isRunning && state.actionInProgress != "cancel-query") {
            scope.launch {
                state = store.beginAction(state, "cancel-query")
                state = store.cancelExecution(state)
            }
        }
    }

    PageScaffold(
        eyebrow = "Load Testing Data Platform",
        title = "SQL-консоль по источникам",
        subtitle = "Проверяй доступность sources, выполняй один SQL или SQL-скрипт по выбранным подключениям и сравнивай результат по каждому statement и source.",
        heroClassNames = listOf("hero-card-compact", "sql-console-hero"),
        heroCopyClassNames = listOf("sql-console-hero-copy"),
        heroHeader = {
            Div({ classes("hero-actions", "mb-3") }) {
                SqlConsoleNavActionButton("На главную", hrefValue = "/")
                SqlConsoleNavActionButton("Объекты БД", hrefValue = "/sql-console-objects")
                SqlConsoleNavActionButton("SQL-консоль", active = true)
            }
        },
        heroArt = {
            SqlConsoleHeroArt()
        },
        content = {
            if (state.errorMessage != null) {
                AlertBanner(state.errorMessage ?: "", "warning")
            }
            if (state.successMessage != null) {
                AlertBanner(state.successMessage ?: "", "success")
            }
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
                return@PageScaffold
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
                        onCheckConnections = {
                            scope.launch {
                                state = store.beginAction(state, "check-connections")
                                state = store.checkConnections(state)
                            }
                        },
                        onMaxRowsDraftChange = { value ->
                            state = store.updateMaxRowsPerShardDraft(state, value)
                        },
                        onTimeoutDraftChange = { value ->
                            state = store.updateQueryTimeoutDraft(state, value)
                        },
                        onSaveSettings = {
                            scope.launch {
                                state = store.beginAction(state, "save-settings")
                                state = store.saveSettings(state)
                            }
                        },
                        onToggleSource = { sourceName, selected ->
                            state = store.updateSelectedSources(state, sourceName, selected)
                        },
                        onCredentialsFileSelected = { file ->
                            selectedCredentialsFile = file
                        },
                        onUploadCredentials = {
                            val file = selectedCredentialsFile ?: return@SqlConsoleSourceSidebar
                            scope.launch {
                                credentialsUploadInProgress = true
                                credentialsMessage = null
                                runCatching {
                                    uploadCredentialsFile(httpClient, file)
                                }.onSuccess { uploaded ->
                                    credentialsStatus = uploaded
                                    credentialsMessage = "credential.properties загружен."
                                    credentialsMessageLevel = "success"
                                    state = store.checkConnections(state.copy(actionInProgress = null))
                                }.onFailure { error ->
                                    credentialsMessage = error.message ?: "Не удалось загрузить credential.properties."
                                    credentialsMessageLevel = "warning"
                                }
                                credentialsUploadInProgress = false
                            }
                        },
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
                        onRecentSelected = { selectedRecentQuery = it },
                        onFavoriteSelected = { selectedFavoriteQuery = it },
                        onApplyRecent = { state = store.applyRecentQuery(state, selectedRecentQuery) },
                        onApplyFavorite = { state = store.applyFavoriteQuery(state, selectedFavoriteQuery) },
                        onRememberFavorite = { state = store.rememberFavoriteQuery(state) },
                        onRemoveFavorite = {
                            state = store.removeFavoriteQuery(state, selectedFavoriteQuery)
                            selectedFavoriteQuery = ""
                        },
                        onClearRecent = {
                            state = store.clearRecentQueries(state)
                            selectedRecentQuery = ""
                        },
                        onStrictSafetyToggle = { state = store.updateStrictSafety(state, !state.strictSafetyEnabled) },
                        onAutoCommitToggle = { state = store.updateAutoCommitEnabled(state, it) },
                        onInsertFavoriteObject = { favorite, sql ->
                            insertSqlText(
                                editor = editorInstance,
                                text = sql,
                                currentValue = state.draftSql,
                                onFallback = { nextSql -> state = store.updateDraftSql(state, nextSql) },
                            )
                        },
                        onOpenFavoriteMetadata = { favorite ->
                            window.location.href = buildFavoriteMetadataHref(favorite)
                        },
                        onRemoveFavoriteObject = { favorite ->
                            state = store.removeFavoriteObject(state, favorite)
                        },
                        onJumpToLine = { lineNumber -> focusEditorLine(editorInstance, lineNumber) },
                        onEditorReady = { editor ->
                            val monacoEditor = editor.asDynamic()
                            editorInstance = monacoEditor
                            monacoEditor.onDidChangeCursorPosition { event ->
                                editorCursorLine = event.position.lineNumber as Int
                            }
                            registerSqlConsoleEditorShortcuts(
                                editor = monacoEditor,
                                onRun = { runAllAction?.invoke() },
                                onRunCurrent = { runCurrentAction?.invoke() },
                                onFormat = { formatSqlAction?.invoke() },
                                onStop = { stopAction?.invoke() },
                            )
                        },
                        onDraftSqlChange = { next -> state = store.updateDraftSql(state, next) },
                        onPageSizeChange = { nextPageSize -> state = store.updatePageSize(state, nextPageSize) },
                        onFormatSql = { formatSqlAction?.invoke() },
                        onRunCurrent = { runCurrentAction?.invoke() },
                        onRunAll = { runAllAction?.invoke() },
                        onStop = { stopAction?.invoke() },
                        onCommit = {
                            scope.launch {
                                state = store.beginAction(state, "commit-query")
                                state = store.commitExecution(state)
                            }
                        },
                        onRollback = {
                            scope.launch {
                                state = store.beginAction(state, "rollback-query")
                                state = store.rollbackExecution(state)
                            }
                        },
                        onExportCsv = {
                            val result = exportableResult ?: return@SqlConsoleWorkspacePanel
                            val shardName = activeExportShard ?: return@SqlConsoleWorkspacePanel
                            scope.launch {
                                runCatching {
                                    httpClient.downloadPostJson(
                                        path = "/api/sql-console/export/source-csv",
                                        payload = SqlConsoleExportRequest(
                                            result = result,
                                            shardName = shardName,
                                        ),
                                        serializer = SqlConsoleExportRequest.serializer(),
                                        fallbackFileName = "$shardName.csv",
                                    )
                                }.onFailure { error ->
                                    state = state.copy(
                                        errorMessage = error.message ?: "Не удалось скачать CSV.",
                                        successMessage = null,
                                    )
                                }
                            }
                        },
                        onExportZip = {
                            val result = exportableResult ?: return@SqlConsoleWorkspacePanel
                            scope.launch {
                                runCatching {
                                    httpClient.downloadPostJson(
                                        path = "/api/sql-console/export/all-zip",
                                        payload = SqlConsoleExportRequest(result = result),
                                        serializer = SqlConsoleExportRequest.serializer(),
                                        fallbackFileName = "sql-console-results.zip",
                                    )
                                }.onFailure { error ->
                                    state = state.copy(
                                        errorMessage = error.message ?: "Не удалось скачать ZIP.",
                                        successMessage = null,
                                    )
                                }
                            }
                        },
                        onSelectStatement = {
                            selectedStatementIndex = it
                            selectedResultShard = null
                            currentDataPage = 1
                        },
                        onSelectOutputTab = { activeOutputTab = it },
                        onSelectShard = {
                            selectedResultShard = it
                            currentDataPage = 1
                        },
                        onSelectPage = { currentDataPage = it },
                    )
                }
            }
        },
    )
}
