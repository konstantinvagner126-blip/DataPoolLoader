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
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                currentExecution = snapshot,
                currentExecutionId = snapshot.id,
            )
        }.getOrElse { error ->
            current.copy(
                actionInProgress = null,
                errorMessage = error.message ?: "Не удалось обновить состояние SQL-запроса.",
            )
        }
    }

    suspend fun cancelExecution(current: SqlConsolePageState): SqlConsolePageState {
        val executionId = current.currentExecutionId ?: return current
        return runCatching {
            val snapshot = api.cancelExecution(executionId)
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Запрос помечен на остановку.",
                currentExecution = snapshot,
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

    suspend fun commitExecution(current: SqlConsolePageState): SqlConsolePageState {
        val executionId = current.currentExecutionId ?: return current
        return runCatching {
            val snapshot = api.commitExecution(executionId)
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Транзакция зафиксирована.",
                currentExecution = snapshot,
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

    suspend fun rollbackExecution(current: SqlConsolePageState): SqlConsolePageState {
        val executionId = current.currentExecutionId ?: return current
        return runCatching {
            val snapshot = api.rollbackExecution(executionId)
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Транзакция откатана.",
                currentExecution = snapshot,
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
