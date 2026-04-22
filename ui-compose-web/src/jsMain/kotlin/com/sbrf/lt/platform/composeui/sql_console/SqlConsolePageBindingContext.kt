package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import kotlinx.coroutines.CoroutineScope

internal class SqlConsolePageBindingContext(
    val store: SqlConsoleStore,
    val scope: CoroutineScope,
    val httpClient: ComposeHttpClient,
    private val currentStateProvider: () -> SqlConsolePageState,
    private val setStateProvider: (SqlConsolePageState) -> Unit,
    private val currentUiStateProvider: () -> SqlConsolePageUiState,
    private val setUiStateProvider: (SqlConsolePageUiState) -> Unit,
    val currentOutlineItem: () -> SqlScriptOutlineItem?,
    val pendingManualTransaction: () -> Boolean,
    val isRunning: () -> Boolean,
    val exportableResult: () -> SqlConsoleQueryResult?,
    val activeExportShard: () -> String?,
) {
    fun currentState(): SqlConsolePageState = currentStateProvider()

    fun setState(state: SqlConsolePageState) {
        setStateProvider(state)
    }

    fun currentUiState(): SqlConsolePageUiState = currentUiStateProvider()

    fun updateState(transform: (SqlConsolePageState) -> SqlConsolePageState) {
        setState(transform(currentState()))
    }

    fun updateUiState(transform: (SqlConsolePageUiState) -> SqlConsolePageUiState) {
        setUiStateProvider(transform(currentUiState()))
    }
}
