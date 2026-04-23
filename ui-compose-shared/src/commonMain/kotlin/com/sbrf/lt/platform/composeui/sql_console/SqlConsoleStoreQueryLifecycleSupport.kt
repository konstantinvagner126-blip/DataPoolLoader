package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleStoreQueryLifecycleSupport(
    private val api: SqlConsoleApi,
) {
    suspend fun startQuery(
        current: SqlConsolePageState,
        workspaceId: String,
        ownerSessionId: String,
        sqlOverride: String? = null,
        successMessage: String = "Запрос запущен.",
    ): SqlConsolePageState {
        val sql = (sqlOverride ?: current.draftSql).trim()
        if (sql.isBlank()) {
            return current.copy(
                actionInProgress = null,
                errorMessage = "Введи SQL-запрос.",
                successMessage = null,
            )
        }
        val analysis = analyzeSqlStatement(sql)
        if (current.strictSafetyEnabled && !analysis.readOnly) {
            return current.copy(
                actionInProgress = null,
                errorMessage = "Строгая защита включена. Для выполнения изменяющего запроса сначала отключи этот режим.",
                successMessage = null,
            )
        }
        return runCatching {
            val started = api.startQuery(
                SqlConsoleQueryStartRequest(
                    sql = sql,
                    selectedSourceNames = current.selectedSourceNames,
                    workspaceId = workspaceId,
                    ownerSessionId = ownerSessionId,
                    transactionMode = current.transactionMode,
                ),
            )
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = successMessage,
                currentExecutionId = started.id,
                currentExecution = SqlConsoleExecutionResponse(
                    id = started.id,
                    status = started.status,
                    startedAt = started.startedAt,
                    cancelRequested = started.cancelRequested,
                    autoCommitEnabled = started.autoCommitEnabled,
                    transactionState = started.transactionState,
                    ownerToken = started.ownerToken,
                    ownerLeaseExpiresAt = started.ownerLeaseExpiresAt,
                    pendingCommitExpiresAt = started.pendingCommitExpiresAt,
                ),
                recentQueries = rememberQuery(current.recentQueries, sql, limit = 15),
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось запустить запрос.",
                successMessage = null,
            )
        }
    }

    suspend fun refreshExecution(current: SqlConsolePageState): SqlConsolePageState {
        val executionId = current.currentExecutionId ?: return current
        return runCatching {
            val snapshot = api.loadExecution(executionId)
            current.applyExecutionSnapshot(snapshot).copy(
                actionInProgress = null,
                errorMessage = null,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось обновить состояние SQL-запроса.",
            )
        }
    }

    suspend fun restoreExecution(
        current: SqlConsolePageState,
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsolePageState {
        return runCatching {
            val loaded = api.loadExecution(executionId)
            val trackingExecution = loaded.status == "RUNNING" || loaded.transactionState == "PENDING_COMMIT"
            val restored = if (trackingExecution) {
                api.heartbeatExecution(
                    executionId = executionId,
                    request = SqlConsoleExecutionOwnerActionRequest(
                        ownerSessionId = ownerSessionId,
                        ownerToken = ownerToken,
                    ),
                )
            } else {
                loaded
            }
            current.applyExecutionSnapshot(restored).copy(
                loading = false,
                actionInProgress = null,
                errorMessage = null,
            )
        }.getOrElse { error ->
            current.clearExecutionOwnership(
                error.message ?: "Не удалось восстановить SQL execution session после обновления страницы.",
            )
        }
    }
}
