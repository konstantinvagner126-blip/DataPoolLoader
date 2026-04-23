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
                sourceStatuses = mergeSourceConnectionStatuses(current.sourceStatuses, result.sourceResults),
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

    suspend fun heartbeatExecution(
        current: SqlConsolePageState,
        ownerSessionId: String,
    ): SqlConsolePageState {
        val executionId = current.currentExecutionId ?: return current
        val actionRequest = current.ownerActionRequest(ownerSessionId) ?: return current
        return runCatching {
            val snapshot = api.heartbeatExecution(executionId, actionRequest)
            current.applyExecutionSnapshot(snapshot).copy(
                actionInProgress = null,
                errorMessage = null,
            )
        }.getOrElse { error ->
            current.handleOwnershipFailure(
                error = error,
                fallbackMessage = "Не удалось подтвердить владение SQL execution session.",
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
            current.applyExecutionSnapshot(snapshot).copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Запрос помечен на остановку.",
            )
        }.getOrElse { error ->
            current.handleOwnershipFailure(error, "Не удалось остановить запрос.")
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
            current.applyExecutionSnapshot(snapshot).copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Транзакция зафиксирована.",
            )
        }.getOrElse { error ->
            current.handleOwnershipFailure(error, "Не удалось выполнить commit.")
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
            current.applyExecutionSnapshot(snapshot).copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Транзакция откатана.",
            )
        }.getOrElse { error ->
            current.handleOwnershipFailure(error, "Не удалось выполнить rollback.")
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

private fun SqlConsolePageState.applyExecutionSnapshot(
    snapshot: SqlConsoleExecutionResponse,
): SqlConsolePageState {
    val execution = snapshot.withOwnerToken(currentExecution?.ownerToken)
    return copy(
        currentExecution = execution,
        currentExecutionId = execution.id,
        sourceStatuses = mergeSourceConnectionStatuses(
            current = sourceStatuses,
            observed = observedExecutionSourceStatuses(execution.result),
        ),
    )
}

private fun SqlConsolePageState.handleOwnershipFailure(
    error: Throwable,
    fallbackMessage: String,
): SqlConsolePageState {
    val message = error.message ?: fallbackMessage
    return if (message.indicatesOwnershipLoss()) {
        clearExecutionOwnership(message)
    } else {
        copy(
            actionInProgress = null,
            errorMessage = message,
            successMessage = null,
        )
    }
}

private fun SqlConsolePageState.clearExecutionOwnership(message: String): SqlConsolePageState =
    copy(
        actionInProgress = null,
        errorMessage = message,
        successMessage = null,
        currentExecution = currentExecution?.copy(ownerToken = null),
    )

private fun String.indicatesOwnershipLoss(): Boolean =
    contains("не принадлежит", ignoreCase = true) ||
        contains("control-path", ignoreCase = true) ||
        contains("потеряла владельца", ignoreCase = true) ||
        contains("владелец", ignoreCase = true) && contains("потерян", ignoreCase = true)
