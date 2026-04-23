package com.sbrf.lt.platform.composeui.sql_console

import kotlinx.coroutines.launch

internal class SqlConsoleExecutionBindings(
    private val context: SqlConsolePageBindingContext,
) {
    fun runAll() {
        launchQuery(
            actionName = "run-query",
            successMessage = "Весь script запущен.",
        )
    }

    fun runCurrent() {
        val statementSql = context.currentOutlineItem()?.sql?.trim().orEmpty()
        if (statementSql.isBlank()) {
            context.updateState {
                it.copy(
                    actionInProgress = null,
                    errorMessage = "Под курсором нет SQL statement для выполнения.",
                    successMessage = null,
                )
            }
            return
        }
        launchQuery(
            actionName = "run-current-query",
            sqlOverride = statementSql,
            successMessage = "Текущий statement запущен.",
        )
    }

    fun runSelection() {
        val selectedSql = context.currentUiState().selectedSqlText.trim()
        if (selectedSql.isBlank()) {
            context.updateState {
                it.copy(
                    actionInProgress = null,
                    errorMessage = "Выдели SQL-фрагмент в редакторе.",
                    successMessage = null,
                )
            }
            return
        }
        launchQuery(
            actionName = "run-selection-query",
            sqlOverride = selectedSql,
            successMessage = "Выделение запущено.",
        )
    }

    fun formatSql() {
        val state = context.currentState()
        val formattedSql = formatSqlScript(state.draftSql)
        if (formattedSql != state.draftSql) {
            context.setState(
                context.store.updateDraftSql(
                    state.copy(errorMessage = null, successMessage = "SQL отформатирован."),
                    formattedSql,
                ),
            )
        }
    }

    fun stop() {
        val state = context.currentState()
        if (context.isRunning() && state.actionInProgress != "cancel-query") {
            context.scope.launch {
                val cancelState = context.store.beginAction(context.currentState(), "cancel-query")
                context.setState(context.store.cancelExecution(cancelState, context.currentUiState().ownerSessionId))
            }
        }
    }

    fun commit() {
        context.scope.launch {
            val committingState = context.store.beginAction(context.currentState(), "commit-query")
            context.setState(context.store.commitExecution(committingState, context.currentUiState().ownerSessionId))
        }
    }

    fun rollback() {
        context.scope.launch {
            val rollbackState = context.store.beginAction(context.currentState(), "rollback-query")
            context.setState(context.store.rollbackExecution(rollbackState, context.currentUiState().ownerSessionId))
        }
    }

    fun exportCsv() {
        val result = context.exportableResult() ?: return
        val shardName = context.activeExportShard() ?: return
        context.scope.launch {
            runCatching {
                context.httpClient.downloadPostJson(
                    path = "/api/sql-console/export/source-csv",
                    payload = SqlConsoleExportRequest(
                        result = result,
                        shardName = shardName,
                    ),
                    serializer = SqlConsoleExportRequest.serializer(),
                    fallbackFileName = "$shardName.csv",
                )
            }.onFailure { error ->
                context.updateState {
                    it.copy(
                        errorMessage = error.message ?: "Не удалось скачать CSV.",
                        successMessage = null,
                    )
                }
            }
        }
    }

    fun exportZip() {
        val result = context.exportableResult() ?: return
        context.scope.launch {
            runCatching {
                context.httpClient.downloadPostJson(
                    path = "/api/sql-console/export/all-zip",
                    payload = SqlConsoleExportRequest(result = result),
                    serializer = SqlConsoleExportRequest.serializer(),
                    fallbackFileName = "sql-console-results.zip",
                )
            }.onFailure { error ->
                context.updateState {
                    it.copy(
                        errorMessage = error.message ?: "Не удалось скачать ZIP.",
                        successMessage = null,
                    )
                }
            }
        }
    }

    private fun launchQuery(
        actionName: String,
        sqlOverride: String? = null,
        successMessage: String,
    ) {
        val state = context.currentState()
        if (state.actionInProgress == actionName || state.info?.configured != true || context.pendingManualTransaction()) {
            return
        }
        context.scope.launch {
            val runningState = context.store.beginAction(context.currentState(), actionName)
            context.setState(
                context.store.startQuery(
                    current = runningState,
                    workspaceId = context.currentUiState().workspaceId,
                    ownerSessionId = context.currentUiState().ownerSessionId,
                    sqlOverride = sqlOverride,
                    successMessage = successMessage,
                ),
            )
        }
    }
}
