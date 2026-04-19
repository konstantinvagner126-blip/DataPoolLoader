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
    val currentExecution = state.currentExecution
    val currentResult = currentExecution?.result
    val statementResults = currentResult.statementResultsOrSelf()
    val activeStatementResult = statementResults.getOrNull(selectedStatementIndex)
    val statementAnalysis = analyzeSqlStatement(state.draftSql)
    val isRunning = currentExecution?.status.equals("RUNNING", ignoreCase = true)
    val runButtonClass = buildRunButtonClass(statementAnalysis, state.strictSafetyEnabled)
    val runtimeContext = state.runtimeContext
    val connectionStatusBySource = state.connectionCheck?.sourceResults?.associateBy { it.sourceName }.orEmpty()
    val exportableResult = activeStatementResult?.toStandaloneQueryResult(currentResult)
    val activeExportShard = activeStatementResult
        ?.takeIf { it.statementType == "RESULT_SET" }
        ?.shardResults
        ?.firstOrNull { it.shardName == selectedResultShard && it.status.equals("SUCCESS", ignoreCase = true) && it.rows.isNotEmpty() }
        ?.shardName

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

            SqlConsoleOverviewStrip(
                state = state,
                connectionStatusText = state.connectionCheck?.let(::buildConnectionCheckStatusText),
            )

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
                            onExecutionPolicyChange = {
                                state = store.updateExecutionPolicy(state, it)
                            },
                            onTransactionModeChange = {
                                state = store.updateTransactionMode(state, it)
                            },
                        )

                        MonacoEditorPane(
                            instanceKey = "compose-sql-console-editor",
                            language = "sql",
                            value = state.draftSql,
                            classNames = listOf("editor-frame", "sql-editor-frame"),
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
                                    classes("btn", runButtonClass, "sql-action-button", "sql-action-button-run")
                                    attr("type", "button")
                                    if (state.actionInProgress == "run-query" || !state.info?.configured.orFalse()) {
                                        disabled()
                                    }
                                    onClick {
                                        scope.launch {
                                            state = store.beginAction(state, "run-query")
                                            state = store.startQuery(state)
                                        }
                                    }
                                }) {
                                    Span({ classes("sql-action-icon", "sql-action-icon-play") })
                                }
                                Button(attrs = {
                                    classes("btn", "btn-danger", "sql-action-button", "sql-action-button-stop")
                                    attr("type", "button")
                                    if (!isRunning || state.actionInProgress == "cancel-query") {
                                        disabled()
                                    }
                                    onClick {
                                        scope.launch {
                                            state = store.beginAction(state, "cancel-query")
                                            state = store.cancelExecution(state)
                                        }
                                    }
                                }) {
                                    Span({ classes("sql-action-icon", "sql-action-icon-stop") })
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
                        ExecutionStatusStrip(currentExecution)

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
private fun SqlConsoleOverviewStrip(
    state: SqlConsolePageState,
    connectionStatusText: String?,
) {
    val execution = state.currentExecution
    val selectedSourcesCount = state.selectedSourceNames.size
    val executionLabel = when {
        execution == null -> "Запусков еще не было"
        execution.status.equals("RUNNING", ignoreCase = true) -> "Выполняется"
        execution.status.equals("SUCCESS", ignoreCase = true) -> "Успешно"
        execution.status.equals("FAILED", ignoreCase = true) -> "Ошибка"
        execution.status.equals("CANCELLED", ignoreCase = true) -> "Остановлен"
        else -> execution.status
    }
    val safetyLabel = if (state.strictSafetyEnabled) "Только read-only" else "Разрешены mutating SQL"
    val executionPolicyLabel = if (state.executionPolicy == "CONTINUE_ON_ERROR") {
        "Продолжать после ошибки"
    } else {
        "Останавливать shard после ошибки"
    }
    val transactionModeLabel = if (state.transactionMode == "TRANSACTION_PER_SHARD") {
        "Одна транзакция на shard"
    } else {
        "Авто-коммит"
    }

    Div({ classes("sql-console-overview-grid", "mb-4") }) {
        SqlConsoleOverviewCard(
            label = "Выбрано sources",
            value = selectedSourcesCount.toString(),
            note = state.selectedSourceNames.joinToString(", ").ifBlank { "Ни один источник пока не выбран." },
        )
        SqlConsoleOverviewCard(
            label = "Guardrail",
            value = safetyLabel,
            note = if (state.strictSafetyEnabled) {
                "Опасные и mutating-команды будут блокироваться до отключения строгой защиты."
            } else {
                "UI предупреждает о mutating и dangerous SQL, но не блокирует их автоматически."
            },
        )
        SqlConsoleOverviewCard(
            label = "Последний запуск",
            value = executionLabel,
            note = connectionStatusText ?: "Проверка подключений еще не выполнялась.",
        )
        SqlConsoleOverviewCard(
            label = "Скрипт policy",
            value = executionPolicyLabel,
            note = if (state.executionPolicy == "CONTINUE_ON_ERROR") {
                "Следующие statement-ы продолжают выполняться даже после ошибки на shard."
            } else {
                "После ошибки на shard последующие statement-ы для него помечаются как SKIPPED."
            },
        )
        SqlConsoleOverviewCard(
            label = "Транзакции",
            value = transactionModeLabel,
            note = if (state.transactionMode == "TRANSACTION_PER_SHARD") {
                "Все statement-ы для одного shard выполняются в одной JDBC-транзакции и откатываются при первой ошибке."
            } else {
                "Каждый statement выполняется в своем обычном auto-commit цикле."
            },
        )
    }
}

@Composable
private fun SqlConsoleOverviewCard(
    label: String,
    value: String,
    note: String,
) {
    Div({ classes("sql-console-overview-card") }) {
        Div({ classes("sync-overview-label") }) { Text(label) }
        Div({ classes("sync-overview-value") }) { Text(value) }
        Div({ classes("sync-overview-note") }) { Text(note) }
    }
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
    onExecutionPolicyChange: (String) -> Unit,
    onTransactionModeChange: (String) -> Unit,
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
                Span { Text("Строгая защита: разрешать только read-only запросы") }
            }
        }
        Div({ classes("sql-query-library-block") }) {
            Label(attrs = {
                classes("small", "text-secondary", "mb-1")
                attr("for", "composeExecutionPolicy")
            }) { Text("Политика выполнения скрипта") }
            Select(attrs = {
                id("composeExecutionPolicy")
                classes("form-select", "form-select-sm", "sql-recent-query-select")
                onChange { onExecutionPolicyChange(it.value ?: "STOP_ON_FIRST_ERROR") }
            }) {
                Option(value = "STOP_ON_FIRST_ERROR", attrs = {
                    if (state.executionPolicy == "STOP_ON_FIRST_ERROR") selected()
                }) { Text("Остановить shard после ошибки") }
                Option(value = "CONTINUE_ON_ERROR", attrs = {
                    if (state.executionPolicy == "CONTINUE_ON_ERROR") selected()
                    if (state.transactionMode == "TRANSACTION_PER_SHARD") disabled()
                }) { Text("Продолжать несмотря на ошибки") }
            }
            if (state.transactionMode == "TRANSACTION_PER_SHARD") {
                Div({ classes("small", "text-secondary", "mt-1") }) {
                    Text("В транзакционном режиме доступна только политика «Остановить shard после ошибки».")
                }
            }
        }
        Div({ classes("sql-query-library-block") }) {
            Label(attrs = {
                classes("small", "text-secondary", "mb-1")
                attr("for", "composeTransactionMode")
            }) { Text("Режим транзакций") }
            Select(attrs = {
                id("composeTransactionMode")
                classes("form-select", "form-select-sm", "sql-recent-query-select")
                onChange { onTransactionModeChange(it.value ?: "AUTO_COMMIT") }
            }) {
                Option(value = "AUTO_COMMIT", attrs = {
                    if (state.transactionMode == "AUTO_COMMIT") selected()
                }) { Text("Авто-коммит") }
                Option(value = "TRANSACTION_PER_SHARD", attrs = {
                    if (state.transactionMode == "TRANSACTION_PER_SHARD") selected()
                }) { Text("Одна транзакция на shard") }
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
private fun ExecutionStatusStrip(execution: SqlConsoleExecutionResponse?) {
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
            "Запрос выполняется с ${execution.startedAt}."
        execution.status.equals("SUCCESS", ignoreCase = true) ->
            "Запрос завершен успешно."
        execution.status.equals("FAILED", ignoreCase = true) ->
            execution.errorMessage ?: "Запрос завершился ошибкой."
        execution.status.equals("CANCELLED", ignoreCase = true) ->
            "Запрос остановлен."
        else -> "Статус запроса: ${execution.status}."
    }
    Div({ classes(*cssClass.split(" ").toTypedArray()) }) {
        Text(text)
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

private fun SqlConsoleStatementResult.toStandaloneQueryResult(source: SqlConsoleQueryResult?): SqlConsoleQueryResult =
    SqlConsoleQueryResult(
        sql = sql,
        statementType = statementType,
        statementKeyword = statementKeyword,
        shardResults = shardResults,
        maxRowsPerShard = source?.maxRowsPerShard ?: 0,
        statementResults = emptyList(),
    )
