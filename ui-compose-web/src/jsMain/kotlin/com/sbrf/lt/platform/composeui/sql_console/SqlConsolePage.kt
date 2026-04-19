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
            runtimeContext?.takeIf { it.hasModeFallback() }?.let { fallbackContext ->
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
