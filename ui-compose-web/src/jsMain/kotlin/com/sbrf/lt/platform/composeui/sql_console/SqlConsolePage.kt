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

@Composable
private fun SqlConsoleWorkspacePanel(
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
private fun SqlConsoleWorkspaceToolbar(
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
                disabled = (
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
                disabled = state.actionInProgress == "run-query" || state.info?.configured != true || pendingManualTransaction,
                extraClasses = arrayOf("sql-action-button", "sql-action-button-run"),
                onClick = onRunAll,
            ) {
                Span({ classes("sql-action-icon", "sql-action-icon-play") })
            }
            SqlToolbarActionButton(
                toneClass = "btn-danger",
                disabled = !isRunning || state.actionInProgress == "cancel-query",
                extraClasses = arrayOf("sql-action-button", "sql-action-button-stop"),
                onClick = onStop,
            ) {
                Span({ classes("sql-action-icon", "sql-action-icon-stop") })
            }
            SqlToolbarActionButton(
                toneClass = "btn-success",
                disabled = !pendingManualTransaction || state.actionInProgress == "commit-query",
                onClick = onCommit,
            ) {
                Text("Commit")
            }
            SqlToolbarActionButton(
                toneClass = "btn-outline-danger",
                disabled = !pendingManualTransaction || state.actionInProgress == "rollback-query",
                onClick = onRollback,
            ) {
                Text("Rollback")
            }
            SqlToolbarActionButton(
                toneClass = "btn-outline-secondary",
                disabled = activeExportShard == null,
                onClick = onExportCsv,
            ) {
                Text("Скачать CSV")
            }
            SqlToolbarActionButton(
                toneClass = "btn-outline-secondary",
                disabled = exportableResult?.statementType != "RESULT_SET",
                onClick = onExportZip,
            ) {
                Text("Скачать ZIP")
            }
        }
    }
}

@Composable
private fun SqlConsoleNavActionButton(
    label: String,
    hrefValue: String? = null,
    active: Boolean = false,
) {
    if (active) {
        Button(attrs = {
            classes("btn", "btn-dark")
            attr("type", "button")
            disabled()
        }) {
            Text(label)
        }
        return
    }
    A(attrs = {
        classes("btn", "btn-outline-secondary")
        href(hrefValue ?: "#")
    }) {
        Text(label)
    }
}

@Composable
private fun SqlConsoleSourceSidebar(
    state: SqlConsolePageState,
    connectionStatusBySource: Map<String, SqlConsoleSourceConnectionStatus>,
    credentialsStatus: CredentialsStatusResponse?,
    credentialsMessage: String?,
    credentialsMessageLevel: String,
    selectedCredentialsFile: File?,
    credentialsUploadInProgress: Boolean,
    onCheckConnections: () -> Unit,
    onMaxRowsDraftChange: (String) -> Unit,
    onTimeoutDraftChange: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onToggleSource: (String, Boolean) -> Unit,
    onCredentialsFileSelected: (File?) -> Unit,
    onUploadCredentials: () -> Unit,
) {
    Div({ classes("panel", "sql-sidebar-panel", "h-100") }) {
        Div({ classes("d-flex", "align-items-center", "justify-content-between", "gap-2", "mb-2") }) {
            Div({ classes("panel-title", "mb-0") }) { Text("Sources") }
            Button(attrs = {
                classes("btn", "btn-outline-dark", "btn-sm")
                attr("type", "button")
                if (state.actionInProgress == "check-connections") {
                    disabled()
                }
                onClick { onCheckConnections() }
            }) {
                Text("Проверить подключение")
            }
        }
        Div({ classes("small", "text-secondary", "mb-3") }) {
            Text(buildConsoleInfoText(state.info))
        }
        Div({ classes("sql-source-settings", "mt-3") }) {
            Label(attrs = {
                classes("small", "text-secondary", "mb-1")
                attr("for", "composeSqlMaxRows")
            }) { Text("Лимит строк на source") }
            Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                Input(type = InputType.Number, attrs = {
                    id("composeSqlMaxRows")
                    classes("form-control", "form-control-sm", "sql-source-limit-input")
                    value(state.maxRowsPerShardDraft)
                    onInput { onMaxRowsDraftChange(it.value?.toString().orEmpty()) }
                })
            }
            Label(attrs = {
                classes("small", "text-secondary", "mt-3", "mb-1")
                attr("for", "composeSqlTimeout")
            }) { Text("Таймаут запроса, сек") }
            Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                Input(type = InputType.Number, attrs = {
                    id("composeSqlTimeout")
                    classes("form-control", "form-control-sm", "sql-source-limit-input")
                    value(state.queryTimeoutSecDraft)
                    onInput { onTimeoutDraftChange(it.value?.toString().orEmpty()) }
                })
                Button(attrs = {
                    classes("btn", "btn-outline-secondary", "btn-sm")
                    attr("type", "button")
                    if (state.actionInProgress == "save-settings") {
                        disabled()
                    }
                    onClick { onSaveSettings() }
                }) {
                    Text("Сохранить")
                }
            }
        }
        state.connectionCheck?.let { connectionCheck ->
            Div({ classes("small", "text-secondary", "mt-2") }) {
                Text(buildConnectionCheckStatusText(connectionCheck))
            }
        } ?: Div({ classes("small", "text-secondary", "mt-2") }) {
            Text("Проверка подключений еще не выполнялась.")
        }
        Div({ classes("small", "text-secondary", "mt-3") }) {
            Text("Выбери, по каким источникам выполнять запрос.")
        }
        SqlConsoleSourceSelectionBlock(
            sourceNames = state.info?.sourceNames.orEmpty(),
            selectedSourceNames = state.selectedSourceNames,
            connectionStatusBySource = connectionStatusBySource,
            onToggleSource = onToggleSource,
        )
        SqlConsoleCredentialsPanel(
            credentialsStatus = credentialsStatus,
            credentialsMessage = credentialsMessage,
            credentialsMessageLevel = credentialsMessageLevel,
            selectedCredentialsFile = selectedCredentialsFile,
            credentialsUploadInProgress = credentialsUploadInProgress,
            onFileSelected = onCredentialsFileSelected,
            onUpload = onUploadCredentials,
        )
    }
}

@Composable
private fun SqlConsoleSourceSelectionBlock(
    sourceNames: List<String>,
    selectedSourceNames: List<String>,
    connectionStatusBySource: Map<String, SqlConsoleSourceConnectionStatus>,
    onToggleSource: (String, Boolean) -> Unit,
) {
    Div({ classes("mt-3", "sql-source-selection") }) {
        sourceNames.forEach { sourceName ->
            val sourceStatus = connectionStatusBySource[sourceName]
            val selected = sourceName in selectedSourceNames
            SqlConsoleSourceCheckbox(
                sourceName = sourceName,
                sourceStatus = sourceStatus,
                selected = selected,
                onToggle = { onToggleSource(sourceName, !selected) },
            )
        }
    }
}

@Composable
private fun SqlConsoleSourceCheckbox(
    sourceName: String,
    sourceStatus: SqlConsoleSourceConnectionStatus?,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Label(attrs = {
        classes("sql-source-checkbox", "sql-source-checkbox-${sourceStatusTone(sourceStatus)}")
        if (selected) {
            classes("sql-source-checkbox-selected")
        }
    }) {
        Input(type = InputType.Checkbox, attrs = {
            if (selected) {
                attr("checked", "checked")
            }
            onClick { onToggle() }
        })
        Div({ classes("sql-source-checkbox-body") }) {
            Div({ classes("sql-source-checkbox-head") }) {
                Span({ classes("sql-source-checkbox-name") }) {
                    Text(sourceName)
                }
                Span({ classes("sql-source-checkbox-status") }) {
                    Text(sourceStatus?.let { translateSourceStatus(it.status) } ?: "Не проверено")
                }
            }
            Div({ classes("sql-source-checkbox-message") }) {
                Text(
                    sourceStatus?.errorMessage
                        ?: sourceStatus?.message
                        ?: "Подключение еще не проверялось. Выбери source и запусти проверку.",
                )
            }
        }
    }
}

@Composable
private fun SqlConsoleCredentialsPanel(
    credentialsStatus: CredentialsStatusResponse?,
    credentialsMessage: String?,
    credentialsMessageLevel: String,
    selectedCredentialsFile: File?,
    credentialsUploadInProgress: Boolean,
    onFileSelected: (File?) -> Unit,
    onUpload: () -> Unit,
) {
    Div({ classes("sql-credentials-details", "mt-4") }) {
        Div({ classes("sql-credentials-summary") }) {
            Text("credential.properties")
        }
        Div({ classes("mt-3") }) {
            Div({ classes("small", "text-secondary", "mb-2") }) {
                Text(credentialsStatus?.let(::buildCredentialsStatusText) ?: "Файл не загружен.")
            }
            if (!credentialsMessage.isNullOrBlank()) {
                AlertBanner(credentialsMessage ?: "", credentialsMessageLevel)
            }
            Div({ classes("d-flex", "flex-wrap", "align-items-center", "gap-2", "mt-3") }) {
                Input(type = InputType.File, attrs = {
                    classes("form-control")
                    attr("accept", ".properties,text/plain")
                    onChange {
                        val input = it.target as? HTMLInputElement
                        onFileSelected(input?.files?.item(0))
                    }
                })
                Button(attrs = {
                    classes("btn", "btn-outline-dark")
                    attr("type", "button")
                    if (selectedCredentialsFile == null || credentialsUploadInProgress) {
                        disabled()
                    }
                    onClick { onUpload() }
                }) {
                    Text("Загрузить файл")
                }
            }
        }
    }
}

@Composable
private fun SqlToolbarActionButton(
    toneClass: String,
    disabled: Boolean = false,
    extraClasses: Array<String> = emptyArray(),
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Button(attrs = {
        classes("btn", toneClass, *extraClasses)
        attr("type", "button")
        if (disabled) {
            disabled()
        }
        onClick { onClick() }
    }) {
        content()
    }
}
