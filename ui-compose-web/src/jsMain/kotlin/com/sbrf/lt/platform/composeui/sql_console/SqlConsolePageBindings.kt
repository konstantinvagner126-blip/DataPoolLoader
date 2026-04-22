package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.foundation.http.uploadCredentialsFile
import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.w3c.files.File

internal data class SqlConsolePageUiState(
    val editorInstance: Any? = null,
    val editorCursorLine: Int = 1,
    val selectedRecentQuery: String = "",
    val selectedFavoriteQuery: String = "",
    val credentialsStatus: CredentialsStatusResponse? = null,
    val selectedCredentialsFile: File? = null,
    val credentialsUploadInProgress: Boolean = false,
    val credentialsMessage: String? = null,
    val credentialsMessageLevel: String = "success",
    val activeOutputTab: String = "data",
    val selectedStatementIndex: Int = 0,
    val selectedResultShard: String? = null,
    val currentDataPage: Int = 1,
    val runningClockTick: Int = 0,
)

internal data class SqlConsolePageCallbacks(
    val onCheckConnections: () -> Unit,
    val onMaxRowsDraftChange: (String) -> Unit,
    val onTimeoutDraftChange: (String) -> Unit,
    val onSaveSettings: () -> Unit,
    val onToggleSource: (String, Boolean) -> Unit,
    val onCredentialsFileSelected: (File?) -> Unit,
    val onUploadCredentials: () -> Unit,
    val onRecentSelected: (String) -> Unit,
    val onFavoriteSelected: (String) -> Unit,
    val onApplyRecent: () -> Unit,
    val onApplyFavorite: () -> Unit,
    val onRememberFavorite: () -> Unit,
    val onRemoveFavorite: () -> Unit,
    val onClearRecent: () -> Unit,
    val onStrictSafetyToggle: () -> Unit,
    val onAutoCommitToggle: (Boolean) -> Unit,
    val onInsertFavoriteObject: (SqlConsoleFavoriteObject, String) -> Unit,
    val onOpenFavoriteMetadata: (SqlConsoleFavoriteObject) -> Unit,
    val onRemoveFavoriteObject: (SqlConsoleFavoriteObject) -> Unit,
    val onJumpToLine: (Int) -> Unit,
    val onEditorReady: (Any) -> Unit,
    val onDraftSqlChange: (String) -> Unit,
    val onPageSizeChange: (Int) -> Unit,
    val onFormatSql: () -> Unit,
    val onRunCurrent: () -> Unit,
    val onRunAll: () -> Unit,
    val onStop: () -> Unit,
    val onCommit: () -> Unit,
    val onRollback: () -> Unit,
    val onExportCsv: () -> Unit,
    val onExportZip: () -> Unit,
    val onSelectStatement: (Int) -> Unit,
    val onSelectOutputTab: (String) -> Unit,
    val onSelectShard: (String?) -> Unit,
    val onSelectPage: (Int) -> Unit,
)

internal fun sqlConsolePageCallbacks(
    store: SqlConsoleStore,
    scope: CoroutineScope,
    httpClient: ComposeHttpClient,
    currentState: () -> SqlConsolePageState,
    setState: (SqlConsolePageState) -> Unit,
    currentUiState: () -> SqlConsolePageUiState,
    setUiState: (SqlConsolePageUiState) -> Unit,
    currentOutlineItem: () -> SqlScriptOutlineItem?,
    pendingManualTransaction: () -> Boolean,
    isRunning: () -> Boolean,
    exportableResult: () -> SqlConsoleQueryResult?,
    activeExportShard: () -> String?,
): SqlConsolePageCallbacks {
    fun updateState(transform: (SqlConsolePageState) -> SqlConsolePageState) {
        setState(transform(currentState()))
    }

    fun updateUiState(transform: (SqlConsolePageUiState) -> SqlConsolePageUiState) {
        setUiState(transform(currentUiState()))
    }

    fun runAll() {
        val state = currentState()
        if (state.actionInProgress != "run-query" && state.info?.configured == true && !pendingManualTransaction()) {
            scope.launch {
                val runningState = store.beginAction(currentState(), "run-query")
                setState(store.startQuery(runningState))
            }
        }
    }

    fun runCurrent() {
        val statementSql = currentOutlineItem()?.sql?.trim().orEmpty()
        val state = currentState()
        if (
            statementSql.isNotBlank() &&
            state.actionInProgress != "run-current-query" &&
            state.info?.configured == true &&
            !pendingManualTransaction()
        ) {
            scope.launch {
                val runningState = store.beginAction(currentState(), "run-current-query")
                setState(
                    store.startQuery(
                        current = runningState,
                        sqlOverride = statementSql,
                        successMessage = "Текущий statement запущен.",
                    ),
                )
            }
        }
    }

    fun formatSql() {
        val state = currentState()
        val formattedSql = formatSqlScript(state.draftSql)
        if (formattedSql != state.draftSql) {
            setState(
                store.updateDraftSql(
                    state.copy(errorMessage = null, successMessage = "SQL отформатирован."),
                    formattedSql,
                ),
            )
        }
    }

    fun stop() {
        val state = currentState()
        if (isRunning() && state.actionInProgress != "cancel-query") {
            scope.launch {
                val cancelState = store.beginAction(currentState(), "cancel-query")
                setState(store.cancelExecution(cancelState))
            }
        }
    }

    fun uploadCredentials() {
        val file = currentUiState().selectedCredentialsFile ?: return
        scope.launch {
            updateUiState {
                it.copy(
                    credentialsUploadInProgress = true,
                    credentialsMessage = null,
                )
            }
            runCatching {
                uploadCredentialsFile(httpClient, file)
            }.onSuccess { uploaded ->
                updateUiState {
                    it.copy(
                        credentialsStatus = uploaded,
                        credentialsMessage = "credential.properties загружен.",
                        credentialsMessageLevel = "success",
                        credentialsUploadInProgress = false,
                    )
                }
                setState(store.checkConnections(currentState().copy(actionInProgress = null)))
            }.onFailure { error ->
                updateUiState {
                    it.copy(
                        credentialsMessage = error.message ?: "Не удалось загрузить credential.properties.",
                        credentialsMessageLevel = "warning",
                        credentialsUploadInProgress = false,
                    )
                }
            }
        }
    }

    fun exportCsv() {
        val result = exportableResult() ?: return
        val shardName = activeExportShard() ?: return
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
                updateState {
                    it.copy(
                        errorMessage = error.message ?: "Не удалось скачать CSV.",
                        successMessage = null,
                    )
                }
            }
        }
    }

    fun exportZip() {
        val result = exportableResult() ?: return
        scope.launch {
            runCatching {
                httpClient.downloadPostJson(
                    path = "/api/sql-console/export/all-zip",
                    payload = SqlConsoleExportRequest(result = result),
                    serializer = SqlConsoleExportRequest.serializer(),
                    fallbackFileName = "sql-console-results.zip",
                )
            }.onFailure { error ->
                updateState {
                    it.copy(
                        errorMessage = error.message ?: "Не удалось скачать ZIP.",
                        successMessage = null,
                    )
                }
            }
        }
    }

    fun onEditorReady(editor: Any) {
        val monacoEditor = editor.asDynamic()
        updateUiState { it.copy(editorInstance = monacoEditor) }
        monacoEditor.onDidChangeCursorPosition { event ->
            updateUiState { current -> current.copy(editorCursorLine = event.position.lineNumber as Int) }
        }
        registerSqlConsoleEditorShortcuts(
            editor = monacoEditor,
            onRun = ::runAll,
            onRunCurrent = ::runCurrent,
            onFormat = ::formatSql,
            onStop = ::stop,
        )
    }

    return SqlConsolePageCallbacks(
        onCheckConnections = {
            scope.launch {
                val checkingState = store.beginAction(currentState(), "check-connections")
                setState(store.checkConnections(checkingState))
            }
        },
        onMaxRowsDraftChange = { value -> updateState { store.updateMaxRowsPerShardDraft(it, value) } },
        onTimeoutDraftChange = { value -> updateState { store.updateQueryTimeoutDraft(it, value) } },
        onSaveSettings = {
            scope.launch {
                val savingState = store.beginAction(currentState(), "save-settings")
                setState(store.saveSettings(savingState))
            }
        },
        onToggleSource = { sourceName, selected ->
            updateState { store.updateSelectedSources(it, sourceName, selected) }
        },
        onCredentialsFileSelected = { file ->
            updateUiState {
                it.copy(selectedCredentialsFile = file)
            }
        },
        onUploadCredentials = ::uploadCredentials,
        onRecentSelected = { selected ->
            updateUiState { it.copy(selectedRecentQuery = selected) }
        },
        onFavoriteSelected = { selected ->
            updateUiState { it.copy(selectedFavoriteQuery = selected) }
        },
        onApplyRecent = {
            updateState { store.applyRecentQuery(it, currentUiState().selectedRecentQuery) }
        },
        onApplyFavorite = {
            updateState { store.applyFavoriteQuery(it, currentUiState().selectedFavoriteQuery) }
        },
        onRememberFavorite = {
            updateState { store.rememberFavoriteQuery(it) }
        },
        onRemoveFavorite = {
            updateState { store.removeFavoriteQuery(it, currentUiState().selectedFavoriteQuery) }
            updateUiState { it.copy(selectedFavoriteQuery = "") }
        },
        onClearRecent = {
            updateState { store.clearRecentQueries(it) }
            updateUiState { it.copy(selectedRecentQuery = "") }
        },
        onStrictSafetyToggle = {
            updateState { store.updateStrictSafety(it, !it.strictSafetyEnabled) }
        },
        onAutoCommitToggle = { enabled ->
            updateState { store.updateAutoCommitEnabled(it, enabled) }
        },
        onInsertFavoriteObject = { _, sql ->
            insertSqlText(
                editor = currentUiState().editorInstance,
                text = sql,
                currentValue = currentState().draftSql,
                onFallback = { nextSql ->
                    updateState { store.updateDraftSql(it, nextSql) }
                },
            )
        },
        onOpenFavoriteMetadata = { favorite ->
            window.location.href = buildFavoriteMetadataHref(favorite)
        },
        onRemoveFavoriteObject = { favorite ->
            updateState { store.removeFavoriteObject(it, favorite) }
        },
        onJumpToLine = { lineNumber ->
            focusEditorLine(currentUiState().editorInstance, lineNumber)
        },
        onEditorReady = ::onEditorReady,
        onDraftSqlChange = { next ->
            updateState { store.updateDraftSql(it, next) }
        },
        onPageSizeChange = { nextPageSize ->
            updateState { store.updatePageSize(it, nextPageSize) }
        },
        onFormatSql = ::formatSql,
        onRunCurrent = ::runCurrent,
        onRunAll = ::runAll,
        onStop = ::stop,
        onCommit = {
            scope.launch {
                val committingState = store.beginAction(currentState(), "commit-query")
                setState(store.commitExecution(committingState))
            }
        },
        onRollback = {
            scope.launch {
                val rollbackState = store.beginAction(currentState(), "rollback-query")
                setState(store.rollbackExecution(rollbackState))
            }
        },
        onExportCsv = ::exportCsv,
        onExportZip = ::exportZip,
        onSelectStatement = { index ->
            updateUiState {
                it.copy(
                    selectedStatementIndex = index,
                    selectedResultShard = null,
                    currentDataPage = 1,
                )
            }
        },
        onSelectOutputTab = { tab ->
            updateUiState { it.copy(activeOutputTab = tab) }
        },
        onSelectShard = { shardName ->
            updateUiState {
                it.copy(
                    selectedResultShard = shardName,
                    currentDataPage = 1,
                )
            }
        },
        onSelectPage = { page ->
            updateUiState { it.copy(currentDataPage = page) }
        },
    )
}
