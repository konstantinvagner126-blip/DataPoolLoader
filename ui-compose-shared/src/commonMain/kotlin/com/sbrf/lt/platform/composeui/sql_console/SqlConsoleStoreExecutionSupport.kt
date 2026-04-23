package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleStoreExecutionSupport(
    private val api: SqlConsoleApi,
) {
    suspend fun saveSettings(current: SqlConsolePageState): SqlConsolePageState {
        val maxRows = current.maxRowsPerShardDraft.toIntOrNull()
        if (maxRows == null || maxRows <= 0) {
            return current.copy(
                actionInProgress = null,
                errorMessage = "Укажи корректный лимит строк: целое число больше 0.",
                successMessage = null,
            )
        }
        val timeoutValue = current.queryTimeoutSecDraft.trim()
        val timeout = when {
            timeoutValue.isBlank() -> null
            else -> timeoutValue.toIntOrNull()?.takeIf { it > 0 }
        }
        if (timeoutValue.isNotBlank() && timeout == null) {
            return current.copy(
                actionInProgress = null,
                errorMessage = "Укажи корректный таймаут: пусто или целое число больше 0.",
                successMessage = null,
            )
        }
        return runCatching {
            val info = api.saveSettings(SqlConsoleSettingsUpdate(maxRows, timeout))
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Настройки SQL-консоли сохранены.",
                info = info,
                maxRowsPerShardDraft = info.maxRowsPerShard.toString(),
                queryTimeoutSecDraft = info.queryTimeoutSec?.toString().orEmpty(),
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось сохранить настройки SQL-консоли.",
                successMessage = null,
            )
        }
    }

    suspend fun checkConnections(current: SqlConsolePageState): SqlConsolePageState =
        runCatching {
            val result = api.checkConnections()
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Проверка подключений завершена.",
                connectionCheck = result,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось проверить подключения.",
                successMessage = null,
            )
        }

    suspend fun startQuery(
        current: SqlConsolePageState,
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
            val preservedToken = current.currentExecution?.ownerToken
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                currentExecution = snapshot.withOwnerToken(preservedToken),
                currentExecutionId = snapshot.id,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось обновить состояние SQL-запроса.",
            )
        }
    }

    suspend fun heartbeatExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState {
        val executionId = current.currentExecutionId ?: return current
        val actionRequest = current.ownerActionRequest(ownerSessionId) ?: return current
        return runCatching {
            val snapshot = api.heartbeatExecution(executionId, actionRequest)
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                currentExecution = snapshot.withOwnerToken(current.currentExecution?.ownerToken),
                currentExecutionId = snapshot.id,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось подтвердить владение SQL execution session.",
                successMessage = null,
            )
        }
    }

    suspend fun cancelExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState {
        val executionId = current.currentExecutionId ?: return current
        val actionRequest = current.ownerActionRequest(ownerSessionId)
            ?: return current.copy(
                actionInProgress = null,
                errorMessage = "Execution session больше не принадлежит этой вкладке.",
                successMessage = null,
            )
        return runCatching {
            val snapshot = api.cancelExecution(executionId, actionRequest)
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Запрос помечен на остановку.",
                currentExecution = snapshot.withOwnerToken(current.currentExecution?.ownerToken),
                currentExecutionId = snapshot.id,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось остановить запрос.",
                successMessage = null,
            )
        }
    }

    suspend fun commitExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState {
        val executionId = current.currentExecutionId ?: return current
        val actionRequest = current.ownerActionRequest(ownerSessionId)
            ?: return current.copy(
                actionInProgress = null,
                errorMessage = "Execution session больше не принадлежит этой вкладке.",
                successMessage = null,
            )
        return runCatching {
            val snapshot = api.commitExecution(executionId, actionRequest)
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Транзакция зафиксирована.",
                currentExecution = snapshot.withOwnerToken(current.currentExecution?.ownerToken),
                currentExecutionId = snapshot.id,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось выполнить commit.",
                successMessage = null,
            )
        }
    }

    suspend fun rollbackExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState {
        val executionId = current.currentExecutionId ?: return current
        val actionRequest = current.ownerActionRequest(ownerSessionId)
            ?: return current.copy(
                actionInProgress = null,
                errorMessage = "Execution session больше не принадлежит этой вкладке.",
                successMessage = null,
            )
        return runCatching {
            val snapshot = api.rollbackExecution(executionId, actionRequest)
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Транзакция откатана.",
                currentExecution = snapshot.withOwnerToken(current.currentExecution?.ownerToken),
                currentExecutionId = snapshot.id,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось выполнить rollback.",
                successMessage = null,
            )
        }
    }
}

private fun SqlConsolePageState.ownerActionRequest(ownerSessionId: String): SqlConsoleExecutionOwnerActionRequest? {
    val ownerToken = currentExecution?.ownerToken ?: return null
    return SqlConsoleExecutionOwnerActionRequest(
        ownerSessionId = ownerSessionId,
        ownerToken = ownerToken,
    )
}

private fun SqlConsoleExecutionResponse.withOwnerToken(ownerToken: String?): SqlConsoleExecutionResponse =
    copy(
        ownerToken = when {
            this.ownerToken != null -> this.ownerToken
            status == "RUNNING" || transactionState == "PENDING_COMMIT" -> ownerToken
            else -> null
        },
    )
