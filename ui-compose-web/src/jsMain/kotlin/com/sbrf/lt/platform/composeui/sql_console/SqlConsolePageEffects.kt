package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.foundation.http.loadCredentialsStatus
import com.sbrf.lt.platform.composeui.foundation.updates.PollingEffect
import kotlinx.coroutines.delay
import kotlinx.browser.window
import org.w3c.dom.events.Event

@Composable
internal fun SqlConsolePageEffects(
    store: SqlConsoleStore,
    httpClient: ComposeHttpClient,
    currentState: () -> SqlConsolePageState,
    setState: (SqlConsolePageState) -> Unit,
    currentUiState: () -> SqlConsolePageUiState,
    setUiState: (SqlConsolePageUiState) -> Unit,
    currentResult: SqlConsoleQueryResult?,
    statementResults: List<SqlConsoleStatementResult>,
    isRunning: Boolean,
    currentExecution: SqlConsoleExecutionResponse?,
) {
    fun updateUiState(transform: (SqlConsolePageUiState) -> SqlConsolePageUiState) {
        setUiState(transform(currentUiState()))
    }

    val trackingExecution = isRunning || currentExecution?.transactionState == "PENDING_COMMIT"
    val heartbeatEnabled = trackingExecution && !currentExecution?.ownerToken.isNullOrBlank()

    LaunchedEffect(store) {
        setState(store.startLoading(currentState()))
        val loadedState = store.load()
        val restoredState = loadSqlConsoleExecutionOwnerState()
            ?.takeIf {
                canRestoreSqlConsoleExecutionOwnership(
                    storedOwnerSessionId = it.ownerSessionId,
                    storedOwnerTabInstanceId = it.ownerTabInstanceId,
                    currentOwnerSessionId = currentUiState().ownerSessionId,
                    currentOwnerTabInstanceId = currentUiState().ownerTabInstanceId,
                )
            }
            ?.let { ownerState ->
                store.restoreExecution(
                    current = loadedState,
                    executionId = ownerState.executionId,
                    ownerSessionId = ownerState.ownerSessionId,
                    ownerToken = ownerState.ownerToken,
                )
            }
            ?: loadedState
        setState(restoredState)
        setUiState(currentUiState().copy(credentialsStatus = loadCredentialsStatus(httpClient)))
    }

    LaunchedEffect(
        currentResult?.statementType,
        currentResult?.statementKeyword,
        currentResult?.statementResults?.joinToString("\u0001") { result ->
            result.statementKeyword + ":" + result.shardResults.joinToString(",") { shard -> shard.shardName }
        },
        currentResult?.shardResults?.joinToString("\u0001") { it.shardName },
    ) {
        val uiState = currentUiState()
        if (currentResult == null) {
            setUiState(
                uiState.copy(
                    activeOutputTab = "data",
                    selectedStatementIndex = 0,
                    selectedResultShard = null,
                    currentDataPage = 1,
                ),
            )
        } else {
            val normalizedStatementIndex = uiState.selectedStatementIndex.coerceIn(0, statementResults.lastIndex.coerceAtLeast(0))
            val resultForDisplay = statementResults.getOrNull(normalizedStatementIndex)
            if (resultForDisplay?.statementType == "RESULT_SET") {
                val successfulShards = resultForDisplay.shardResults.filter { it.status.equals("SUCCESS", ignoreCase = true) }
                setUiState(
                    uiState.copy(
                        activeOutputTab = "data",
                        selectedStatementIndex = normalizedStatementIndex,
                        selectedResultShard = successfulShards.firstOrNull { it.shardName == uiState.selectedResultShard }?.shardName
                            ?: successfulShards.firstOrNull()?.shardName,
                        currentDataPage = if (uiState.selectedResultShard in successfulShards.map { it.shardName }) uiState.currentDataPage else 1,
                    ),
                )
            } else {
                setUiState(
                    uiState.copy(
                        activeOutputTab = "status",
                        selectedStatementIndex = normalizedStatementIndex,
                        selectedResultShard = null,
                        currentDataPage = 1,
                    ),
                )
            }
        }
    }

    LaunchedEffect(
        currentState().currentExecutionId,
        currentExecution?.status,
        currentExecution?.transactionState,
        currentExecution?.ownerToken,
    ) {
        val executionId = currentState().currentExecutionId
        val ownerToken = currentExecution?.ownerToken
        val trackingOwnedExecution = executionId != null &&
            ownerToken != null &&
            (currentExecution.status == "RUNNING" || currentExecution.transactionState == "PENDING_COMMIT")
        if (trackingOwnedExecution) {
            saveSqlConsoleExecutionOwnerState(
                SqlConsoleExecutionOwnerState(
                    executionId = executionId,
                    ownerSessionId = currentUiState().ownerSessionId,
                    ownerTabInstanceId = currentUiState().ownerTabInstanceId,
                    ownerToken = ownerToken,
                ),
            )
        } else {
            clearSqlConsoleExecutionOwnerState()
        }
    }

    DisposableEffect(
        currentState().currentExecutionId,
        currentExecution?.ownerToken,
        currentExecution?.status,
        currentExecution?.transactionState,
        currentUiState().ownerSessionId,
    ) {
        val executionId = currentState().currentExecutionId
        val ownerToken = currentExecution?.ownerToken
        val releaseEnabled = executionId != null &&
            ownerToken != null &&
            (currentExecution.status == "RUNNING" || currentExecution.transactionState == "PENDING_COMMIT")
        if (!releaseEnabled) {
            onDispose { }
        } else {
            val ownerSessionId = currentUiState().ownerSessionId
            val releaseHandler: (Event) -> Unit = {
                tryReleaseSqlConsoleExecutionOwnership(
                    executionId = requireNotNull(executionId),
                    ownerSessionId = ownerSessionId,
                    ownerToken = requireNotNull(ownerToken),
                )
            }
            window.addEventListener("pagehide", releaseHandler)
            onDispose {
                window.removeEventListener("pagehide", releaseHandler)
            }
        }
    }

    LaunchedEffect(
        currentState().draftSql,
        currentState().selectedSourceNames.joinToString("\u0001"),
        currentState().pageSize,
        currentState().strictSafetyEnabled,
        currentState().recentQueries.joinToString("\u0001"),
        currentState().favoriteQueries.joinToString("\u0001"),
        currentState().favoriteObjects.joinToString("\u0001") { favorite ->
            favorite.sourceName + "|" + favorite.schemaName + "|" + favorite.objectName + "|" + favorite.objectType + "|" + (favorite.tableName ?: "")
        },
    ) {
        val state = currentState()
        if (state.loading || state.info == null) {
            return@LaunchedEffect
        }
        delay(500)
        setState(store.persistState(state))
    }

    PollingEffect(
        enabled = trackingExecution,
        intervalMs = 2000,
        onTick = {
            setState(store.refreshExecution(currentState()))
        },
    )

    PollingEffect(
        enabled = heartbeatEnabled,
        intervalMs = 5000,
        onTick = {
            setState(
                store.heartbeatExecution(
                    current = currentState(),
                    ownerSessionId = currentUiState().ownerSessionId,
                ),
            )
        },
    )

    LaunchedEffect(isRunning, currentExecution?.startedAt) {
        if (!isRunning) {
            updateUiState { it.copy(runningClockTick = 0) }
            return@LaunchedEffect
        }
        while (true) {
            updateUiState { uiState ->
                uiState.copy(runningClockTick = uiState.runningClockTick + 1)
            }
            delay(1000)
        }
    }
}
