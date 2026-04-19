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
import org.w3c.xhr.FormData
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
    val runButtonClass = buildRunButtonClass(statementAnalysis, state.strictSafetyEnabled)
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
        if (state.actionInProgress != "run-query" && state.info?.configured.orFalse() && !pendingManualTransaction) {
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
            state.info?.configured.orFalse() &&
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
                A(attrs = {
                    classes("btn", "btn-outline-secondary")
                    href("/")
                }) { Text("На главную") }
                A(attrs = {
                    classes("btn", "btn-outline-secondary")
                    href("/sql-console-objects")
                }) { Text("Объекты БД") }
                Button(attrs = {
                    classes("btn", "btn-dark")
                    attr("type", "button")
                    disabled()
                }) { Text("SQL-консоль") }
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
            runtimeContext?.takeIf { it.requestedMode != it.effectiveMode }?.let { fallbackContext ->
                AlertBanner(buildSqlConsoleFallbackWarning(fallbackContext), "warning")
            }

            if (state.loading && state.info == null) {
                LoadingStateCard(title = "SQL-консоль", text = "Конфигурация SQL-консоли загружается.")
                return@PageScaffold
            }

            Div({ classes("row", "g-4") }) {
                Div({ classes("col-12", "col-xl-3") }) {
                    Div({ classes("panel", "sql-sidebar-panel", "h-100") }) {
                        Div({ classes("d-flex", "align-items-center", "justify-content-between", "gap-2", "mb-2") }) {
                            Div({ classes("panel-title", "mb-0") }) { Text("Sources") }
                            Button(attrs = {
                                classes("btn", "btn-outline-dark", "btn-sm")
                                attr("type", "button")
                                if (state.actionInProgress == "check-connections") {
                                    disabled()
                                }
                                onClick {
                                    scope.launch {
                                        state = store.beginAction(state, "check-connections")
                                        state = store.checkConnections(state)
                                    }
                                }
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
                                    onInput { state = store.updateMaxRowsPerShardDraft(state, it.value?.toString().orEmpty()) }
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
                                    onInput { state = store.updateQueryTimeoutDraft(state, it.value?.toString().orEmpty()) }
                                })
                                Button(attrs = {
                                    classes("btn", "btn-outline-secondary", "btn-sm")
                                    attr("type", "button")
                                    if (state.actionInProgress == "save-settings") {
                                        disabled()
                                    }
                                    onClick {
                                        scope.launch {
                                            state = store.beginAction(state, "save-settings")
                                            state = store.saveSettings(state)
                                        }
                                    }
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
                        Div({ classes("mt-3", "sql-source-selection") }) {
                            state.info?.sourceNames?.forEach { sourceName ->
                                val sourceStatus = connectionStatusBySource[sourceName]
                                val selected = sourceName in state.selectedSourceNames
                                Label(attrs = {
                                    classes("sql-source-checkbox", sourceStatusCardClass(sourceStatus))
                                    if (selected) {
                                        classes("sql-source-checkbox-selected")
                                    }
                                }) {
                                    Input(type = InputType.Checkbox, attrs = {
                                        if (selected) {
                                            attr("checked", "checked")
                                        }
                                        onClick {
                                            state = store.updateSelectedSources(state, sourceName, !selected)
                                        }
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
                        }
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
                                            selectedCredentialsFile = input?.files?.item(0)
                                        }
                                    })
                                    Button(attrs = {
                                        classes("btn", "btn-outline-dark")
                                        attr("type", "button")
                                        if (selectedCredentialsFile == null || credentialsUploadInProgress) {
                                            disabled()
                                        }
                                        onClick {
                                            val file = selectedCredentialsFile ?: return@onClick
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
                                        }
                                    }) {
                                        Text("Загрузить файл")
                                    }
                                }
                            }
                        }
                    }
                }

                Div({ classes("col-12", "col-xl-9") }) {
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
                            onRecentSelected = { selectedRecentQuery = it },
                            onFavoriteSelected = { selectedFavoriteQuery = it },
                            onApplyRecent = {
                                state = store.applyRecentQuery(state, selectedRecentQuery)
                            },
                            onApplyFavorite = {
                                state = store.applyFavoriteQuery(state, selectedFavoriteQuery)
                            },
                            onRememberFavorite = {
                                state = store.rememberFavoriteQuery(state)
                            },
                            onRemoveFavorite = {
                                state = store.removeFavoriteQuery(state, selectedFavoriteQuery)
                                selectedFavoriteQuery = ""
                            },
                            onClearRecent = {
                                state = store.clearRecentQueries(state)
                                selectedRecentQuery = ""
                            },
                            onStrictSafetyToggle = {
                                state = store.updateStrictSafety(state, !state.strictSafetyEnabled)
                            },
                            onAutoCommitToggle = {
                                state = store.updateAutoCommitEnabled(state, it)
                            },
                        )

                        SqlFavoriteObjectsBlock(
                            favorites = state.favoriteObjects,
                            onInsert = { favorite ->
                                insertSqlText(
                                    editor = editorInstance,
                                    text = favorite.qualifiedName(),
                                    currentValue = state.draftSql,
                                    onFallback = { nextSql ->
                                        state = store.updateDraftSql(state, nextSql)
                                    },
                                )
                            },
                            onInsertSelect = { favorite ->
                                insertSqlText(
                                    editor = editorInstance,
                                    text = buildFavoritePreviewSql(favorite),
                                    currentValue = state.draftSql,
                                    onFallback = { nextSql ->
                                        state = store.updateDraftSql(state, nextSql)
                                    },
                                )
                            },
                            onInsertCount = { favorite ->
                                insertSqlText(
                                    editor = editorInstance,
                                    text = buildFavoriteCountSql(favorite),
                                    currentValue = state.draftSql,
                                    onFallback = { nextSql ->
                                        state = store.updateDraftSql(state, nextSql)
                                    },
                                )
                            },
                            onOpenMetadata = { favorite ->
                                window.location.href = buildFavoriteMetadataHref(favorite)
                            },
                            onRemove = { favorite ->
                                state = store.removeFavoriteObject(state, favorite)
                            },
                        )

                        SqlEditorIdeBlock(
                            outlineItems = scriptOutline,
                            currentLine = editorCursorLine,
                            currentItem = currentOutlineItem,
                            selectedSourceCount = state.selectedSourceNames.size,
                            onJumpToLine = { lineNumber ->
                                focusEditorLine(editorInstance, lineNumber)
                            },
                        )

                        MonacoEditorPane(
                            instanceKey = "compose-sql-console-editor",
                            language = "sql",
                            value = state.draftSql,
                            classNames = listOf("editor-frame", "sql-editor-frame"),
                            onEditorReady = { editor ->
                                editorInstance = editor
                                editor.onDidChangeCursorPosition { event ->
                                    editorCursorLine = event.position.lineNumber as Int
                                }
                                registerSqlConsoleEditorShortcuts(
                                    editor = editor,
                                    onRun = { runAllAction?.invoke() },
                                    onRunCurrent = { runCurrentAction?.invoke() },
                                    onFormat = { formatSqlAction?.invoke() },
                                    onStop = { stopAction?.invoke() },
                                )
                            },
                            onValueChange = { next ->
                                state = store.updateDraftSql(state, next)
                            },
                        )

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
                                        state = store.updatePageSize(state, it.value?.toIntOrNull() ?: 50)
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
                                Button(attrs = {
                                    classes("btn", "btn-outline-dark")
                                    attr("type", "button")
                                    onClick { formatSqlAction?.invoke() }
                                }) {
                                    Text("Форматировать")
                                }
                                Button(attrs = {
                                    classes("btn", "btn-outline-dark")
                                    attr("type", "button")
                                    if (
                                        state.actionInProgress == "run-current-query" ||
                                        !state.info?.configured.orFalse() ||
                                        pendingManualTransaction ||
                                        currentOutlineItem == null
                                    ) {
                                        disabled()
                                    }
                                    onClick { runCurrentAction?.invoke() }
                                }) {
                                    Text("Текущий")
                                }
                                Button(attrs = {
                                    classes("btn", runButtonClass, "sql-action-button", "sql-action-button-run")
                                    attr("type", "button")
                                    if (state.actionInProgress == "run-query" || !state.info?.configured.orFalse() || pendingManualTransaction) {
                                        disabled()
                                    }
                                    onClick { runAllAction?.invoke() }
                                }) {
                                    Span({ classes("sql-action-icon", "sql-action-icon-play") })
                                }
                                Button(attrs = {
                                    classes("btn", "btn-danger", "sql-action-button", "sql-action-button-stop")
                                    attr("type", "button")
                                    if (!isRunning || state.actionInProgress == "cancel-query") {
                                        disabled()
                                    }
                                    onClick { stopAction?.invoke() }
                                }) {
                                    Span({ classes("sql-action-icon", "sql-action-icon-stop") })
                                }
                                Button(attrs = {
                                    classes("btn", "btn-success")
                                    attr("type", "button")
                                    if (!pendingManualTransaction || state.actionInProgress == "commit-query") {
                                        disabled()
                                    }
                                    onClick {
                                        scope.launch {
                                            state = store.beginAction(state, "commit-query")
                                            state = store.commitExecution(state)
                                        }
                                    }
                                }) {
                                    Text("Commit")
                                }
                                Button(attrs = {
                                    classes("btn", "btn-outline-danger")
                                    attr("type", "button")
                                    if (!pendingManualTransaction || state.actionInProgress == "rollback-query") {
                                        disabled()
                                    }
                                    onClick {
                                        scope.launch {
                                            state = store.beginAction(state, "rollback-query")
                                            state = store.rollbackExecution(state)
                                        }
                                    }
                                }) {
                                    Text("Rollback")
                                }
                                Button(attrs = {
                                    classes("btn", "btn-outline-secondary")
                                    attr("type", "button")
                                    if (activeExportShard == null) {
                                        disabled()
                                    }
                                    onClick {
                                        val result = exportableResult ?: return@onClick
                                        val shardName = activeExportShard ?: return@onClick
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
                                    }
                                }) {
                                    Text("Скачать CSV")
                                }
                                Button(attrs = {
                                    classes("btn", "btn-outline-secondary")
                                    attr("type", "button")
                                    if (exportableResult?.statementType != "RESULT_SET") {
                                        disabled()
                                    }
                                    onClick {
                                        val result = exportableResult ?: return@onClick
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
                                    }
                                }) {
                                    Text("Скачать ZIP")
                                }
                            }
                        }

                        CommandGuardrail(analysis = statementAnalysis, strictSafetyEnabled = state.strictSafetyEnabled)
                        ExecutionStatusStrip(currentExecution, runningClockTick)

                        Div({ classes("sql-output-panel") }) {
                            StatementSelectionBlock(
                                statementResults = statementResults,
                                selectedStatementIndex = selectedStatementIndex,
                                onSelectStatement = {
                                    selectedStatementIndex = it
                                    selectedResultShard = null
                                    currentDataPage = 1
                                },
                            )
                            QueryOutputPanel(
                                execution = currentExecution,
                                result = exportableResult,
                                pageSize = state.pageSize,
                                activeTab = activeOutputTab,
                                selectedShard = selectedResultShard,
                                currentPage = currentDataPage,
                                onSelectTab = { activeOutputTab = it },
                                onSelectShard = {
                                    selectedResultShard = it
                                    currentDataPage = 1
                                },
                                onSelectPage = { currentDataPage = it },
                            )
                        }
                    }
                }
            }
        },
    )
}

private fun buildSqlConsoleFallbackWarning(runtimeContext: com.sbrf.lt.platform.composeui.model.RuntimeContext): String {
    val requestedLabel = if (runtimeContext.requestedMode == ModuleStoreMode.DATABASE) "База данных" else "Файлы"
    val effectiveLabel = if (runtimeContext.effectiveMode == ModuleStoreMode.DATABASE) "База данных" else "Файлы"
    val reason = runtimeContext.fallbackReason?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    return "Запрошен режим «$requestedLabel», но сейчас активен «$effectiveLabel». SQL-консоль доступна, однако экраны модулей работают по текущему runtime-context.$reason"
}

@Composable
private fun SqlConsoleHeroArt() {
    Div({ classes("sql-console-stage") }) {
        Div({ classes("sql-console-node", "sql-console-node-sources") }) { Text("SOURCES") }
        Div({ classes("sql-console-node", "sql-console-node-check") }) { Text("CHECK") }
        Div({ classes("sql-console-node", "sql-console-node-sql") }) { Text("SQL") }

        Div({ classes("sql-console-line", "sql-console-line-left-top") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-left-top") })
        }
        Div({ classes("sql-console-line", "sql-console-line-left-middle") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-left-middle") })
        }
        Div({ classes("sql-console-line", "sql-console-line-left-bottom") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-left-bottom") })
        }

        Div({ classes("sql-console-hub") }) {
            Div({ classes("merge-title") }) { Text("QUERY") }
            Div({ classes("merge-subtitle") }) { Text("RUNNER") }
        }

        Div({ classes("sql-console-line", "sql-console-line-right-top") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-right-top") })
        }
        Div({ classes("sql-console-line", "sql-console-line-right-bottom") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-right-bottom") })
        }

        Div({ classes("sql-console-node", "sql-console-node-results") }) { Text("RESULTS") }
        Div({ classes("sql-console-node", "sql-console-node-status") }) { Text("STATUS") }
    }
}

@Composable
private fun QueryLibraryBlock(
    state: SqlConsolePageState,
    selectedRecentQuery: String,
    selectedFavoriteQuery: String,
    onRecentSelected: (String) -> Unit,
    onFavoriteSelected: (String) -> Unit,
    onApplyRecent: () -> Unit,
    onApplyFavorite: () -> Unit,
    onRememberFavorite: () -> Unit,
    onRemoveFavorite: () -> Unit,
    onClearRecent: () -> Unit,
    onStrictSafetyToggle: () -> Unit,
    onAutoCommitToggle: (Boolean) -> Unit,
) {
    Div({ classes("sql-query-library", "mb-3") }) {
        Div({ classes("sql-query-library-row") }) {
            Div({ classes("sql-query-library-block") }) {
                Label(attrs = {
                    classes("small", "text-secondary", "mb-1")
                    attr("for", "composeRecentQueries")
                }) { Text("Последние запросы") }
                Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                    Select(attrs = {
                        id("composeRecentQueries")
                        classes("form-select", "form-select-sm", "sql-recent-query-select")
                        onChange { onRecentSelected(it.value ?: "") }
                    }) {
                        Option(value = "") { Text(if (state.recentQueries.isEmpty()) "История пока пуста" else "Выбери запрос") }
                        state.recentQueries.forEach { query ->
                            Option(value = query, attrs = { if (selectedRecentQuery == query) selected() }) {
                                Text(query.take(120))
                            }
                        }
                    }
                    Button(attrs = {
                        classes("btn", "btn-outline-secondary", "btn-sm")
                        attr("type", "button")
                        if (selectedRecentQuery.isBlank()) {
                            disabled()
                        }
                        onClick { onApplyRecent() }
                    }) { Text("Подставить") }
                    Button(attrs = {
                        classes("btn", "btn-outline-secondary", "btn-sm")
                        attr("type", "button")
                        onClick { onClearRecent() }
                    }) { Text("Очистить") }
                }
            }
            Div({ classes("sql-query-library-block") }) {
                Label(attrs = {
                    classes("small", "text-secondary", "mb-1")
                    attr("for", "composeFavoriteQueries")
                }) { Text("Избранные запросы") }
                Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                    Select(attrs = {
                        id("composeFavoriteQueries")
                        classes("form-select", "form-select-sm", "sql-recent-query-select")
                        onChange { onFavoriteSelected(it.value ?: "") }
                    }) {
                        Option(value = "") { Text(if (state.favoriteQueries.isEmpty()) "Избранное пока пусто" else "Выбери запрос") }
                        state.favoriteQueries.forEach { query ->
                            Option(value = query, attrs = { if (selectedFavoriteQuery == query) selected() }) {
                                Text(query.take(120))
                            }
                        }
                    }
                    Button(attrs = {
                        classes("btn", "btn-outline-secondary", "btn-sm")
                        attr("type", "button")
                        if (selectedFavoriteQuery.isBlank()) {
                            disabled()
                        }
                        onClick { onApplyFavorite() }
                    }) { Text("Подставить") }
                    Button(attrs = {
                        classes("btn", "btn-outline-primary", "btn-sm")
                        attr("type", "button")
                        onClick { onRememberFavorite() }
                    }) { Text("В избранное") }
                    Button(attrs = {
                        classes("btn", "btn-outline-danger", "btn-sm")
                        attr("type", "button")
                        if (selectedFavoriteQuery.isBlank()) {
                            disabled()
                        }
                        onClick { onRemoveFavorite() }
                    }) { Text("Убрать") }
                }
            }
        }
        Div({ classes("sql-query-library-block") }) {
            Label(attrs = { classes("d-flex", "align-items-center", "gap-2", "small", "text-secondary", "mb-0") }) {
                Input(type = InputType.Checkbox, attrs = {
                    classes("form-check-input")
                    if (state.strictSafetyEnabled) {
                        attr("checked", "checked")
                    }
                    onClick { onStrictSafetyToggle() }
                })
                Span { Text("Read-only") }
            }
        }
        Div({ classes("sql-query-library-block") }) {
            Label(attrs = { classes("d-flex", "align-items-center", "gap-2", "small", "text-secondary", "mb-0") }) {
                Input(type = InputType.Checkbox, attrs = {
                    classes("form-check-input")
                    if (state.transactionMode == "AUTO_COMMIT") {
                        attr("checked", "checked")
                    }
                    onClick { onAutoCommitToggle(state.transactionMode != "AUTO_COMMIT") }
                })
                Span { Text("Autocommit") }
            }
        }
    }
}

@Composable
private fun SqlFavoriteObjectsBlock(
    favorites: List<SqlConsoleFavoriteObject>,
    onInsert: (SqlConsoleFavoriteObject) -> Unit,
    onInsertSelect: (SqlConsoleFavoriteObject) -> Unit,
    onInsertCount: (SqlConsoleFavoriteObject) -> Unit,
    onOpenMetadata: (SqlConsoleFavoriteObject) -> Unit,
    onRemove: (SqlConsoleFavoriteObject) -> Unit,
) {
    if (favorites.isEmpty()) {
        return
    }
    Div({ classes("sql-query-library", "mb-3") }) {
        Div({ classes("d-flex", "align-items-center", "justify-content-between", "gap-3", "mb-2") }) {
            Div({ classes("panel-title", "mb-0") }) { Text("Избранные объекты") }
            Div({ classes("small", "text-secondary") }) {
                Text("Быстрая вставка имен и готовых SQL-шаблонов в редактор.")
            }
        }
        Div({ classes("sql-favorite-objects-grid") }) {
            favorites.forEach { favorite ->
                Div({ classes("sql-favorite-object-card") }) {
                    Div({ classes("sql-favorite-object-meta") }) {
                        Div({ classes("sql-favorite-object-name") }) {
                            Text(favorite.qualifiedName())
                        }
                        Div({ classes("sql-favorite-object-note") }) {
                            Text("${favorite.sourceName} • ${translateFavoriteObjectType(favorite.objectType)}")
                        }
                    }
                    Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                        Button(attrs = {
                            classes("btn", "btn-outline-dark", "btn-sm")
                            attr("type", "button")
                            onClick { onInsert(favorite) }
                        }) { Text("Вставить") }
                        Button(attrs = {
                            classes("btn", "btn-dark", "btn-sm")
                            attr("type", "button")
                            onClick { onInsertSelect(favorite) }
                        }) { Text(if (supportsFavoriteRowPreview(favorite)) "SELECT *" else "В SQL") }
                        if (supportsFavoriteRowPreview(favorite)) {
                            Button(attrs = {
                                classes("btn", "btn-outline-dark", "btn-sm")
                                attr("type", "button")
                                onClick { onInsertCount(favorite) }
                            }) { Text("COUNT(*)") }
                        }
                        Button(attrs = {
                            classes("btn", "btn-outline-secondary", "btn-sm")
                            attr("type", "button")
                            onClick { onOpenMetadata(favorite) }
                        }) { Text("Метаданные") }
                        Button(attrs = {
                            classes("btn", "btn-outline-danger", "btn-sm")
                            attr("type", "button")
                            onClick { onRemove(favorite) }
                        }) { Text("Убрать") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandGuardrail(
    analysis: SqlStatementAnalysis,
    strictSafetyEnabled: Boolean,
) {
    val cssClass = when {
        analysis.keyword == "SQL" -> "sql-guardrail sql-guardrail-neutral"
        strictSafetyEnabled && !analysis.readOnly -> "sql-guardrail sql-guardrail-danger"
        analysis.dangerous -> "sql-guardrail sql-guardrail-danger"
        !analysis.readOnly -> "sql-guardrail sql-guardrail-warning"
        else -> "sql-guardrail sql-guardrail-safe"
    }
    val text = when {
        analysis.keyword == "SQL" -> "Текущий запрос не определен. Введи SQL, чтобы UI показал тип команды и предупредил о потенциально опасных операциях."
        strictSafetyEnabled && !analysis.readOnly -> "Строгая защита включена. Команда ${analysis.keyword} будет заблокирована до отключения этого режима."
        analysis.dangerous -> "Команда ${analysis.keyword} считается потенциально опасной. Перед запуском перепроверь SQL и выбранные источники."
        !analysis.readOnly -> "Команда ${analysis.keyword} может изменить данные или структуру на выбранных источниках."
        else -> "Команда ${analysis.keyword} распознана как read-only."
    }
    Div({ classes(*cssClass.split(" ").toTypedArray()) }) {
        Text(text)
    }
}

@Composable
private fun SqlEditorIdeBlock(
    outlineItems: List<SqlScriptOutlineItem>,
    currentLine: Int,
    currentItem: SqlScriptOutlineItem?,
    selectedSourceCount: Int,
    onJumpToLine: (Int) -> Unit,
) {
    Div({ classes("sql-query-library", "mb-3") }) {
        Div({ classes("sql-query-library-block") }) {
            Div({ classes("small", "text-secondary", "mb-1") }) { Text("Горячие клавиши") }
            Div({ classes("sql-ide-hotkeys") }) {
                Span({ classes("sql-ide-hotkey-chip") }) { Text("Cmd/Ctrl + Enter -> Запустить") }
                Span({ classes("sql-ide-hotkey-chip") }) { Text("Shift + Alt + F -> Форматировать") }
                Span({ classes("sql-ide-hotkey-chip") }) { Text("Esc -> Остановить") }
            }
        }
        Div({ classes("sql-query-library-block") }) {
            Div({ classes("small", "text-secondary", "mb-2") }) { Text("Текущий statement") }
            if (currentItem == null) {
                Div({ classes("sql-current-statement-card") }) {
                    Div({ classes("small", "text-secondary") }) {
                        Text("Поставь курсор внутрь statement-а, чтобы UI показал, что именно запустит action `Текущий`.")
                    }
                }
            } else {
                Div({ classes("sql-current-statement-card") }) {
                    Div({ classes("d-flex", "justify-content-between", "align-items-start", "gap-3", "mb-2") }) {
                        Div {
                            Div({ classes("sql-current-statement-title") }) {
                                Text("${currentItem.keyword} · строки ${currentItem.startLine}-${currentItem.endLine}")
                            }
                            Div({ classes("sql-current-statement-meta") }) {
                                Text("Источников: $selectedSourceCount • Категория: ${statementCategoryText(currentItem)}")
                            }
                        }
                        Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                            StatementRiskBadge(currentItem)
                        }
                    }
                    Div({ classes("sql-current-statement-preview") }) {
                        Text(currentItem.preview)
                    }
                }
            }
        }
        Div({ classes("sql-query-library-block") }) {
            Div({ classes("d-flex", "justify-content-between", "align-items-center", "gap-3", "mb-2") }) {
                Div({ classes("small", "text-secondary") }) { Text("Script outline") }
                Div({ classes("small", "text-secondary") }) { Text("Statement-ов: ${outlineItems.size}") }
            }
            if (outlineItems.isEmpty()) {
                Div({ classes("small", "text-secondary") }) {
                    Text("Введи SQL, чтобы получить карту statement-ов и быстро прыгать по строкам.")
                }
            } else {
                Div({ classes("sql-script-outline") }) {
                    outlineItems.forEach { item ->
                        Button(attrs = {
                            classes(
                                "btn",
                                "btn-sm",
                                "sql-script-outline-item",
                                if (currentLine in item.startLine..item.endLine) "btn-dark" else "btn-outline-secondary",
                            )
                            attr("type", "button")
                            onClick { onJumpToLine(item.startLine) }
                        }) {
                            Div({ classes("d-flex", "justify-content-between", "align-items-start", "gap-3", "w-100") }) {
                                Span({ classes("sql-script-outline-item-main") }) {
                                    Text("#${item.index} ${item.keyword} · строки ${item.startLine}-${item.endLine}")
                                }
                                StatementRiskBadge(item)
                            }
                            Span({ classes("sql-script-outline-item-preview") }) {
                                Text(item.preview)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementRiskBadge(item: SqlScriptOutlineItem) {
    val cssClass = when {
        item.keyword == "SQL" -> "sql-statement-risk-badge sql-statement-risk-neutral"
        item.dangerous -> "sql-statement-risk-badge sql-statement-risk-danger"
        !item.readOnly -> "sql-statement-risk-badge sql-statement-risk-warning"
        else -> "sql-statement-risk-badge sql-statement-risk-safe"
    }
    val text = when {
        item.keyword == "SQL" -> "SQL"
        item.dangerous -> "Опасно"
        !item.readOnly -> "Меняет данные"
        else -> "Read-only"
    }
    Span({ classes(*cssClass.split(" ").toTypedArray()) }) { Text(text) }
}

private fun statementCategoryText(item: SqlScriptOutlineItem): String =
    when {
        item.keyword == "SQL" -> "SQL"
        item.dangerous -> "Опасно"
        !item.readOnly -> "Меняет данные"
        else -> "Read-only"
    }

@Composable
private fun StatementSelectionBlock(
    statementResults: List<SqlConsoleStatementResult>,
    selectedStatementIndex: Int,
    onSelectStatement: (Int) -> Unit,
) {
    if (statementResults.size <= 1) {
        return
    }

    Div({ classes("sql-query-library", "mb-3") }) {
        Div({ classes("small", "text-secondary", "mb-2") }) {
            Text("Скрипт содержит ${statementResults.size} statement-ов. Выбери statement, для которого показывать данные, статусы и экспорт.")
        }
        Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
            statementResults.forEachIndexed { index, statement ->
                Button(attrs = {
                    classes(
                        "btn",
                        "btn-sm",
                        if (index == selectedStatementIndex) "btn-dark" else "btn-outline-secondary",
                    )
                    attr("type", "button")
                    onClick { onSelectStatement(index) }
                }) {
                    Text("#${index + 1} ${statement.statementKeyword}")
                }
            }
        }
    }
}

@Composable
private fun ExecutionStatusStrip(
    execution: SqlConsoleExecutionResponse?,
    runningClockTick: Int,
) {
    val isRunning = execution?.status.equals("RUNNING", ignoreCase = true)
    val showLiveDuration = isRunning && runningClockTick >= 0
    val cssClass = when {
        execution == null -> "sql-status-strip"
        execution.status.equals("FAILED", ignoreCase = true) -> "sql-status-strip sql-status-strip-failed"
        execution.status.equals("SUCCESS", ignoreCase = true) -> "sql-status-strip sql-status-strip-success"
        execution.status.equals("CANCELLED", ignoreCase = true) -> "sql-status-strip sql-status-strip-warning"
        else -> "sql-status-strip sql-status-strip-running"
    }
    val text = when {
        execution == null -> "Запрос пока не выполнялся."
        execution.status.equals("RUNNING", ignoreCase = true) && execution.cancelRequested ->
            "Запрос выполняется, отправлена команда на остановку."
        execution.status.equals("RUNNING", ignoreCase = true) ->
            "Сценарий выполняется."
        execution.transactionState == "PENDING_COMMIT" ->
            "Сценарий выполнен и ждет команды Коммит или Роллбек."
        execution.transactionState == "COMMITTED" ->
            "Транзакция зафиксирована."
        execution.transactionState == "ROLLED_BACK" ->
            "Транзакция откатана."
        execution.status.equals("SUCCESS", ignoreCase = true) ->
            "Запрос завершен успешно."
        execution.status.equals("FAILED", ignoreCase = true) ->
            execution.errorMessage ?: "Запрос завершился ошибкой."
        execution.status.equals("CANCELLED", ignoreCase = true) ->
            "Запрос остановлен."
        else -> "Статус запроса: ${execution.status}."
    }
    Div({ classes(*cssClass.split(" ").toTypedArray()) }) {
        Div({ classes("sql-status-strip-content") }) {
            if (isRunning && execution != null) {
                Div({ classes("run-progress-status-wrap") }) {
                    Div({ classes("run-progress-spinner-arrows") }) {
                        Span({ classes("run-progress-spinner-arrow", "run-progress-spinner-arrow-forward") }) { Text("↻") }
                        Span({ classes("run-progress-spinner-arrow", "run-progress-spinner-arrow-backward") }) { Text("↺") }
                    }
                    Div({ classes("sql-status-strip-copy") }) {
                        Div({ classes("sql-status-strip-title") }) { Text(text) }
                        Div({ classes("sql-status-strip-meta") }) {
                            Text(
                                buildString {
                                    append("Старт: ")
                                    append(formatDateTime(execution.startedAt))
                                    append(" • Прошло: ")
                                    append(formatDuration(execution.startedAt, execution.finishedAt, running = showLiveDuration))
                                },
                            )
                        }
                    }
                }
            } else {
                Div({ classes("sql-status-strip-copy") }) {
                    Div({ classes("sql-status-strip-title") }) { Text(text) }
                    if (execution != null) {
                        Div({ classes("sql-status-strip-meta") }) {
                            Text(
                                buildString {
                                    append("Старт: ")
                                    append(formatDateTime(execution.startedAt))
                                    if (execution.transactionState == "PENDING_COMMIT" && execution.transactionShardNames.isNotEmpty()) {
                                        append(" • Открытых транзакций: ")
                                        append(execution.transactionShardNames.joinToString(", "))
                                    }
                                    if (!execution.finishedAt.isNullOrBlank()) {
                                        append(" • Завершение: ")
                                        append(formatDateTime(execution.finishedAt))
                                        append(" • Длительность: ")
                                        append(formatDuration(execution.startedAt, execution.finishedAt))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueryOutputPanel(
    execution: SqlConsoleExecutionResponse?,
    result: SqlConsoleQueryResult?,
    pageSize: Int,
    activeTab: String,
    selectedShard: String?,
    currentPage: Int,
    onSelectTab: (String) -> Unit,
    onSelectShard: (String) -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    Div({ classes("sql-output-tabs") }) {
        OutputTabButton(
            label = "Данные",
            active = activeTab == "data",
            enabled = true,
            onClick = { onSelectTab("data") },
        )
        OutputTabButton(
            label = "Статусы",
            active = activeTab == "status",
            enabled = true,
            onClick = { onSelectTab("status") },
        )
    }
    Div({
        classes("sql-output-pane")
        if (activeTab == "data") {
            classes("active")
        }
    }) {
        if (activeTab == "data") {
            SelectResultPane(
                execution = execution,
                result = result,
                pageSize = pageSize,
                selectedShard = selectedShard,
                currentPage = currentPage,
                onSelectShard = onSelectShard,
                onSelectPage = onSelectPage,
            )
        }
    }
    Div({
        classes("sql-output-pane")
        if (activeTab == "status") {
            classes("active")
        }
    }) {
        if (activeTab == "status") {
            StatusResultPane(
                execution = execution,
                result = result,
            )
        }
    }
}

@Composable
private fun OutputTabButton(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("sql-output-tab")
        if (active) {
            classes("active")
        }
        attr("type", "button")
        if (!enabled) {
            disabled()
        }
        onClick { onClick() }
    }) {
        Text(label)
    }
}

@Composable
private fun SelectResultPane(
    execution: SqlConsoleExecutionResponse?,
    result: SqlConsoleQueryResult?,
    pageSize: Int,
    selectedShard: String?,
    currentPage: Int,
    onSelectShard: (String) -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    if (execution == null) {
        Div({ classes("text-secondary", "small", "mb-3") }) {
            Text("Пока нет данных для отображения.")
        }
        Div({ classes("sql-result-placeholder") }) {
            Text("Выполни запрос, чтобы увидеть данные со всех shard/source.")
        }
        return
    }

    if (result == null) {
        val message = execution.errorMessage?.takeIf { it.isNotBlank() }
        if (message != null) {
            AlertBanner(message, "danger")
        } else {
            Div({ classes("text-secondary", "small", "mb-3") }) {
                Text("Выполняется запрос...")
            }
            Div({ classes("sql-result-placeholder") }) {
                Text("Ожидается завершение запроса.")
            }
        }
        return
    }

    if (result.statementType != "RESULT_SET") {
        Div({ classes("sql-result-placeholder") }) {
            Text("Команда ${result.statementKeyword} не возвращает табличные данные. Смотри вкладку «Статусы».")
        }
        return
    }

    val successfulShards = result.shardResults.filter { it.status.equals("SUCCESS", ignoreCase = true) && it.rows.isNotEmpty() }
    if (successfulShards.isEmpty()) {
        Div({ classes("sql-result-placeholder") }) {
            Text("Ни один source не вернул данные для отображения.")
        }
        return
    }

    val activeShard = successfulShards.firstOrNull { it.shardName == selectedShard } ?: successfulShards.first()
    val totalPages = maxOf(1, (activeShard.rowCount + pageSize - 1) / pageSize)
    val normalizedPage = currentPage.coerceIn(1, totalPages)
    val startIndex = (normalizedPage - 1) * pageSize
    val endIndexExclusive = minOf(startIndex + pageSize, activeShard.rowCount)
    val visibleRows = activeShard.rows.drop(startIndex).take(pageSize)

    Div({ classes("text-secondary", "small", "mb-3") }) {
        Text(
            "Данные показываются отдельно по каждому source. Лимит на source: ${result.maxRowsPerShard}.",
        )
    }
    Ul({ classes("nav", "nav-tabs", "sql-result-tabs", "mb-3") }) {
        successfulShards.forEach { shard ->
            Li({ classes("nav-item") }) {
                Button(attrs = {
                    classes("nav-link")
                    if (shard.shardName == activeShard.shardName) {
                        classes("active")
                    }
                    attr("type", "button")
                    onClick { onSelectShard(shard.shardName) }
                }) {
                    Text("${shard.shardName} (${shard.rowCount})")
                }
            }
        }
    }
    Div({ classes("small", "text-secondary", "mb-3") }) {
        Text(
            buildString {
                append("Source ")
                append(activeShard.shardName)
                append(". Показано строк: ")
                append(if (activeShard.rowCount == 0) 0 else startIndex + 1)
                append("-")
                append(endIndexExclusive)
                append(" из ")
                append(activeShard.rowCount)
                append(". Страница ")
                append(normalizedPage)
                append(" из ")
                append(totalPages)
                append(".")
                if (activeShard.truncated) {
                    append(" Результат усечен лимитом ${result.maxRowsPerShard} строк на source.")
                }
            },
        )
    }
    Div({ classes("table-responsive") }) {
        Table({ classes("table", "table-sm", "table-striped", "sql-result-table", "mb-0") }) {
            Thead {
                Tr {
                    activeShard.columns.forEach { column ->
                        Th { Text(column) }
                    }
                }
            }
            Tbody {
                visibleRows.forEach { row ->
                    Tr {
                        activeShard.columns.forEach { column ->
                            Td { Text(row[column] ?: "") }
                        }
                    }
                }
            }
        }
    }
    if (totalPages > 1) {
        Div({ classes("sql-pagination-footer") }) {
            Div({ classes("small", "text-secondary") }) {
                Text("Страница $normalizedPage из $totalPages")
            }
            Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                (1..totalPages).forEach { page ->
                    Button(attrs = {
                        classes(
                            "btn",
                            "btn-sm",
                            if (page == normalizedPage) "btn-dark" else "btn-outline-secondary",
                        )
                        attr("type", "button")
                        onClick { onSelectPage(page) }
                    }) {
                        Text(page.toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusResultPane(
    execution: SqlConsoleExecutionResponse?,
    result: SqlConsoleQueryResult?,
) {
    if (execution == null) {
        Div({ classes("sql-result-placeholder") }) {
            Text("Пока нет результатов для отображения.")
        }
        return
    }

    if (result == null) {
        val message = execution.errorMessage?.takeIf { it.isNotBlank() }
        if (message != null) {
            AlertBanner(message, "danger")
        } else {
            Div({ classes("sql-result-placeholder") }) {
                Text("Ожидается завершение запроса.")
            }
        }
        return
    }

    Div({ classes("text-secondary", "small", "mb-3") }) {
        Text(
            buildString {
                append("Тип команды: ")
                append(result.statementKeyword)
                append(" • shard/source: ")
                append(result.shardResults.size)
                append(" • startedAt: ")
                append(execution.startedAt)
                if (!execution.finishedAt.isNullOrBlank()) {
                    append(" • finishedAt: ")
                    append(execution.finishedAt.orEmpty())
                }
            },
        )
    }
    if (result.shardResults.isEmpty()) {
        EmptyStateCard(
            title = "Результаты",
            text = "Сервер не вернул результатов по выбранным shard/source.",
        )
        return
    }
    Div({ classes("table-responsive", "mb-3") }) {
        Table({ classes("table", "table-striped", "table-hover", "align-middle", "mb-0") }) {
            Thead {
                Tr {
                    Th { Text("Source") }
                    Th { Text("Статус") }
                    Th { Text("Старт") }
                    Th { Text("Финиш") }
                    Th { Text("Длительность") }
                    Th { Text("Затронуто строк") }
                    Th { Text("Сообщение") }
                    Th { Text("Ошибка") }
                }
            }
            Tbody {
                result.shardResults.forEach { shard ->
                    Tr {
                        Td {
                            org.jetbrains.compose.web.dom.B { Text(shard.shardName) }
                        }
                        Td { StatusBadge(shard.status) }
                        Td { Text(formatDateTime(shard.startedAt)) }
                        Td { Text(formatDateTime(shard.finishedAt)) }
                        Td { Text(formatDuration(shard.startedAt, shard.finishedAt, running = shard.status.equals("RUNNING", ignoreCase = true))) }
                        Td { Text(shard.affectedRows?.toString() ?: "-") }
                        Td { Text(shard.message ?: "-") }
                        Td { Text(shard.errorMessage ?: "-") }
                    }
                }
            }
        }
    }
    Div({ classes("sql-shard-card-grid") }) {
    result.shardResults.forEach { shard ->
        Div({ classes("sql-shard-card", "status-${statusCssSuffix(shard.status)}") }) {
            Div({ classes("d-flex", "justify-content-between", "align-items-start", "gap-3") }) {
                Div {
                    H3({ classes("h6", "mb-1") }) { Text(shard.shardName) }
                    Div({ classes("small", "text-secondary") }) {
                        Text(
                            buildString {
                                append("Статус: ")
                                append(shard.status)
                                if (shard.affectedRows != null) {
                                    append(" • affectedRows: ")
                                    append(shard.affectedRows)
                                }
                                if (shard.rowCount > 0) {
                                    append(" • rows: ")
                                    append(shard.rowCount)
                                }
                                if (shard.durationMillis != null) {
                                    append(" • длительность: ")
                                    append(formatDurationMillis(shard.durationMillis))
                                } else if (!shard.startedAt.isNullOrBlank()) {
                                    append(" • старт: ")
                                    append(formatDateTime(shard.startedAt))
                                }
                                if (shard.truncated) {
                                    append(" • результат усечен")
                                }
                            },
                        )
                    }
                }
                StatusBadge(shard.status)
            }
            if (!shard.errorMessage.isNullOrBlank()) {
                AlertBanner(shard.errorMessage ?: "", "danger")
            } else if (!shard.message.isNullOrBlank()) {
                Div({ classes("alert", "alert-secondary", "mt-3", "mb-0") }) {
                    Text(shard.message ?: "")
                }
            }
            Div({ classes("sql-shard-card-timings") }) {
                Div { Text("Старт: ${formatDateTime(shard.startedAt)}") }
                Div { Text("Финиш: ${formatDateTime(shard.finishedAt)}") }
                Div {
                    Text(
                        "Длительность: ${
                            if (shard.durationMillis != null) {
                                formatDurationMillis(shard.durationMillis)
                            } else {
                                formatDuration(
                                    shard.startedAt,
                                    shard.finishedAt,
                                    running = shard.status.equals("RUNNING", ignoreCase = true),
                                )
                            }
                        }",
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val cssClass = "status-badge status-${statusCssSuffix(status)}"
    Span({ classes(*cssClass.split(" ").toTypedArray()) }) {
        Text(translateSourceStatus(status))
    }
}

private fun buildConsoleInfoText(info: SqlConsoleInfo?): String =
    when {
        info == null -> "Конфигурация не загружена."
        !info.configured -> "SQL-консоль не настроена. Проверь конфигурацию источников и credential.properties."
        else -> "Доступно источников: ${info.sourceNames.size}. Лимит строк по умолчанию: ${info.maxRowsPerShard}."
    }

private fun buildConnectionCheckStatusText(result: SqlConsoleConnectionCheckResponse): String {
    val success = result.sourceResults.count { it.status.equals("SUCCESS", ignoreCase = true) || it.status.equals("OK", ignoreCase = true) }
    val failed = result.sourceResults.size - success
    return "Проверка подключений завершена. Успешно: $success, с ошибкой: $failed."
}

private fun sourceStatusCardClass(status: SqlConsoleSourceConnectionStatus?): String =
    "sql-source-checkbox-${sourceStatusTone(status)}"

private fun Boolean?.orFalse(): Boolean = this == true

private fun buildRunButtonClass(
    analysis: SqlStatementAnalysis,
    strictSafetyEnabled: Boolean,
): String = "btn-${runButtonTone(analysis, strictSafetyEnabled)}"

private fun statusCssSuffix(status: String): String =
    sourceStatusSuffix(status)

private suspend fun loadCredentialsStatus(
    httpClient: ComposeHttpClient,
): CredentialsStatusResponse? =
    runCatching {
        httpClient.get("/api/credentials", CredentialsStatusResponse.serializer())
    }.getOrNull()

private suspend fun uploadCredentialsFile(
    httpClient: ComposeHttpClient,
    file: File,
): CredentialsStatusResponse {
    val formData = FormData()
    formData.append("file", file, file.name)
    return httpClient.postFormData(
        path = "/api/credentials/upload",
        formData = formData,
        deserializer = CredentialsStatusResponse.serializer(),
    )
}

@kotlinx.serialization.Serializable
private data class SqlConsoleExportRequest(
    val result: SqlConsoleQueryResult,
    val shardName: String? = null,
)

private fun SqlConsoleQueryResult?.statementResultsOrSelf(): List<SqlConsoleStatementResult> =
    when {
        this == null -> emptyList()
        statementResults.isNotEmpty() -> statementResults
        else -> listOf(
            SqlConsoleStatementResult(
                sql = sql,
                statementType = statementType,
                statementKeyword = statementKeyword,
                shardResults = shardResults,
            )
        )
    }

private data class SqlScriptOutlineItem(
    val index: Int,
    val keyword: String,
    val readOnly: Boolean,
    val dangerous: Boolean,
    val startLine: Int,
    val endLine: Int,
    val sql: String,
    val preview: String,
)

private fun parseSqlScriptOutline(sql: String): List<SqlScriptOutlineItem> {
    val items = mutableListOf<SqlScriptOutlineItem>()
    val current = StringBuilder()
    var index = 0
    var line = 1
    var statementStartLine = 1
    var inSingleQuote = false
    var inDoubleQuote = false
    var inLineComment = false
    var inBlockComment = false

    fun flushCurrent() {
        val raw = current.toString().trim()
        if (raw.isNotBlank()) {
            val analysis = analyzeSqlStatement(raw)
            val preview = raw.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                ?.take(120)
                .orEmpty()
            items += SqlScriptOutlineItem(
                index = items.size + 1,
                keyword = analysis.keyword,
                readOnly = analysis.readOnly,
                dangerous = analysis.dangerous,
                startLine = statementStartLine,
                endLine = line.coerceAtLeast(statementStartLine),
                sql = raw,
                preview = preview,
            )
        }
        current.clear()
    }

    while (index < sql.length) {
        val char = sql[index]
        val next = sql.getOrNull(index + 1)

        when {
            inLineComment -> {
                current.append(char)
                if (char == '\n') {
                    inLineComment = false
                }
            }

            inBlockComment -> {
                current.append(char)
                if (char == '*' && next == '/') {
                    current.append(next)
                    index++
                    inBlockComment = false
                }
            }

            inSingleQuote -> {
                current.append(char)
                if (char == '\'' && next == '\'') {
                    current.append(next)
                    index++
                } else if (char == '\'') {
                    inSingleQuote = false
                }
            }

            inDoubleQuote -> {
                current.append(char)
                if (char == '"' && next == '"') {
                    current.append(next)
                    index++
                } else if (char == '"') {
                    inDoubleQuote = false
                }
            }

            char == '-' && next == '-' -> {
                current.append(char).append(next)
                index++
                inLineComment = true
            }

            char == '/' && next == '*' -> {
                current.append(char).append(next)
                index++
                inBlockComment = true
            }

            char == '\'' -> {
                current.append(char)
                inSingleQuote = true
            }

            char == '"' -> {
                current.append(char)
                inDoubleQuote = true
            }

            char == ';' -> {
                flushCurrent()
                statementStartLine = line
            }

            else -> current.append(char)
        }

        if (char == '\n') {
            line += 1
            if (current.isEmpty()) {
                statementStartLine = line
            }
        }
        index++
    }

    flushCurrent()
    return items
}

private fun focusEditorLine(
    editor: dynamic,
    lineNumber: Int,
) {
    if (editor == null) {
        return
    }
    editor.revealLineInCenter(lineNumber)
    val position = js("{}")
    position.lineNumber = lineNumber
    position.column = 1
    editor.setPosition(position)
    editor.focus()
}

private fun insertSqlText(
    editor: dynamic,
    text: String,
    currentValue: String,
    onFallback: (String) -> Unit,
) {
    if (editor == null || editor.executeEdits == undefined) {
        onFallback(appendSqlText(currentValue, text))
        return
    }
    val edit = js("{}")
    edit.range = editor.getSelection()
    edit.text = text
    edit.forceMoveMarkers = true
    editor.executeEdits("compose-sql-console-favorites", arrayOf(edit))
    editor.focus()
}

private fun appendSqlText(
    currentValue: String,
    text: String,
): String =
    when {
        currentValue.isBlank() -> text
        currentValue.last().isWhitespace() -> currentValue + text
        else -> "$currentValue $text"
    }

private fun registerSqlConsoleEditorShortcuts(
    editor: dynamic,
    onRun: () -> Unit,
    onRunCurrent: () -> Unit,
    onFormat: () -> Unit,
    onStop: () -> Unit,
) {
    val monaco = window.asDynamic().monaco ?: return
    val ctrlEnter = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyCode.Enter as Int)
    val ctrlShiftEnter = (monaco.KeyMod.CtrlCmd as Int) or (monaco.KeyMod.Shift as Int) or (monaco.KeyCode.Enter as Int)
    val shiftAltF = (monaco.KeyMod.Shift as Int) or (monaco.KeyMod.Alt as Int) or (monaco.KeyCode.KeyF as Int)
    val escape = monaco.KeyCode.Escape as Int
    editor.addCommand(ctrlEnter) {
        onRun()
    }
    editor.addCommand(ctrlShiftEnter) {
        onRunCurrent()
    }
    editor.addCommand(shiftAltF) {
        onFormat()
    }
    editor.addCommand(escape) {
        onStop()
    }
}

private fun SqlConsoleFavoriteObject.qualifiedName(): String = "${schemaName}.${objectName}"

private fun supportsFavoriteRowPreview(favorite: SqlConsoleFavoriteObject): Boolean =
    when (favorite.objectType.uppercase()) {
        "TABLE", "VIEW", "MATERIALIZED_VIEW" -> true
        else -> false
    }

private fun buildFavoritePreviewSql(favorite: SqlConsoleFavoriteObject): String {
    val qualifiedName = sqlQualifiedName(favorite.schemaName, favorite.objectName)
    return if (supportsFavoriteRowPreview(favorite)) {
        """
        select *
        from $qualifiedName
        limit 100;
        """.trimIndent()
    } else {
        """
        select schemaname,
               tablename,
               indexname,
               indexdef
        from pg_catalog.pg_indexes
        where schemaname = ${sqlLiteral(favorite.schemaName)}
          and indexname = ${sqlLiteral(favorite.objectName)};
        """.trimIndent()
    }
}

private fun buildFavoriteCountSql(favorite: SqlConsoleFavoriteObject): String {
    val qualifiedName = sqlQualifiedName(favorite.schemaName, favorite.objectName)
    return """
        select count(*) as total_rows
        from $qualifiedName;
    """.trimIndent()
}

private fun buildFavoriteMetadataHref(favorite: SqlConsoleFavoriteObject): String =
    "/sql-console-objects?query=${urlEncode(favorite.objectName)}&source=${urlEncode(favorite.sourceName)}&schema=${urlEncode(favorite.schemaName)}&object=${urlEncode(favorite.objectName)}&type=${urlEncode(favorite.objectType)}"

private fun sqlQualifiedName(
    schemaName: String,
    objectName: String,
): String = "${sqlIdentifier(schemaName)}.${sqlIdentifier(objectName)}"

private fun sqlIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""

private fun sqlLiteral(value: String): String = "'${value.replace("'", "''")}'"

private fun urlEncode(value: String): String = js("encodeURIComponent(value)") as String

private fun translateFavoriteObjectType(type: String): String =
    when (type.uppercase()) {
        "TABLE" -> "Таблица"
        "VIEW" -> "Представление"
        "MATERIALIZED_VIEW" -> "Материализованное представление"
        "INDEX" -> "Индекс"
        else -> type
    }

private fun formatSqlScript(sql: String): String {
    val outline = parseSqlScriptOutline(sql)
    if (outline.isEmpty()) {
        return sql.trim()
    }
    return outline.joinToString(separator = ";\n\n") { formatSqlStatement(it.sql) }.trim() +
        if (sql.trimEnd().endsWith(";")) ";" else ""
}

private fun formatSqlStatement(sql: String): String {
    val whitespaceNormalized = sql
        .trim()
        .replace(Regex("\\s+"), " ")

    if (whitespaceNormalized.isBlank()) {
        return ""
    }

    var formatted = whitespaceNormalized
    listOf(
        "WITH",
        "SELECT",
        "FROM",
        "WHERE",
        "GROUP BY",
        "ORDER BY",
        "HAVING",
        "LIMIT",
        "OFFSET",
        "INSERT INTO",
        "UPDATE",
        "DELETE FROM",
        "VALUES",
        "SET",
        "RETURNING",
        "UNION ALL",
        "UNION",
        "LEFT JOIN",
        "RIGHT JOIN",
        "FULL JOIN",
        "INNER JOIN",
        "CROSS JOIN",
        "JOIN",
        "ON",
    ).forEachIndexed { index, keyword ->
        val prefix = if (index == 0) Regex("(?i)\\b$keyword\\b") else Regex("(?i)\\s+\\b$keyword\\b")
        formatted = formatted.replace(prefix, "\n${keyword.uppercase()}")
    }
    formatted = formatted
        .replace(Regex("\n+"), "\n")
        .lines()
        .joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("ON ") -> "    $trimmed"
                trimmed.startsWith("AND ") || trimmed.startsWith("OR ") -> "    $trimmed"
                else -> trimmed
            }
        }
        .trim()
    return formatted
}

private fun SqlConsoleStatementResult.toStandaloneQueryResult(source: SqlConsoleQueryResult?): SqlConsoleQueryResult =
    SqlConsoleQueryResult(
        sql = sql,
        statementType = statementType,
        statementKeyword = statementKeyword,
        shardResults = shardResults,
        maxRowsPerShard = source?.maxRowsPerShard ?: 0,
        statementResults = emptyList(),
    )
