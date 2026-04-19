package com.sbrf.lt.platform.composeui.sql_console

class SqlConsoleStore(
    private val api: SqlConsoleApi,
) {
    suspend fun load(): SqlConsolePageState {
        val runtimeContextResult = runCatching { api.loadRuntimeContext() }
        val runtimeContext = runtimeContextResult.getOrNull()
        if (runtimeContext == null) {
            return SqlConsolePageState(
                loading = false,
                errorMessage = runtimeContextResult.exceptionOrNull()?.message ?: "Не удалось загрузить runtime context SQL-консоли.",
            )
        }

        val infoResult = runCatching { api.loadInfo() }
        val info = infoResult.getOrNull()
        if (info == null) {
            return SqlConsolePageState(
                loading = false,
                runtimeContext = runtimeContext,
                errorMessage = infoResult.exceptionOrNull()?.message ?: "Не удалось загрузить SQL-консоль.",
            )
        }

        val persistedStateResult = runCatching { api.loadState() }
        val persistedState = persistedStateResult.getOrDefault(
            SqlConsoleStateSnapshot(draftSql = "select 1 as check_value"),
        )
        val selectedSources = persistedState.selectedSourceNames
            .filter { it in info.sourceNames }
            .ifEmpty { info.sourceNames }
        val normalizedTransactionMode = normalizeTransactionMode(persistedState.transactionMode)
        val normalizedExecutionPolicy = normalizeExecutionPolicy(
            persistedState.executionPolicy,
            normalizedTransactionMode,
        )
        return SqlConsolePageState(
            loading = false,
            runtimeContext = runtimeContext,
            info = info,
            draftSql = persistedState.draftSql.ifBlank { "select 1 as check_value" },
            recentQueries = persistedState.recentQueries,
            favoriteQueries = persistedState.favoriteQueries,
            selectedSourceNames = selectedSources,
            pageSize = normalizePageSize(persistedState.pageSize),
            strictSafetyEnabled = persistedState.strictSafetyEnabled,
            executionPolicy = normalizedExecutionPolicy,
            transactionMode = normalizedTransactionMode,
            maxRowsPerShardDraft = info.maxRowsPerShard.toString(),
            queryTimeoutSecDraft = info.queryTimeoutSec?.toString().orEmpty(),
            errorMessage = persistedStateResult.exceptionOrNull()?.message,
        )
    }

    fun startLoading(current: SqlConsolePageState): SqlConsolePageState =
        current.copy(loading = true, errorMessage = null, successMessage = null)

    fun updateDraftSql(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        current.copy(draftSql = value)

    fun updateSelectedSources(
        current: SqlConsolePageState,
        sourceName: String,
        enabled: Boolean,
    ): SqlConsolePageState {
        val next = if (enabled) {
            (current.selectedSourceNames + sourceName).distinct()
        } else {
            current.selectedSourceNames.filterNot { it == sourceName }
        }
        return current.copy(selectedSourceNames = next)
    }

    fun updatePageSize(
        current: SqlConsolePageState,
        pageSize: Int,
    ): SqlConsolePageState =
        current.copy(pageSize = normalizePageSize(pageSize))

    fun updateStrictSafety(
        current: SqlConsolePageState,
        enabled: Boolean,
    ): SqlConsolePageState =
        current.copy(strictSafetyEnabled = enabled)

    fun updateExecutionPolicy(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState {
        val normalized = normalizeExecutionPolicy(value, current.transactionMode)
        return current.copy(
            executionPolicy = normalized,
        )
    }

    fun updateTransactionMode(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState {
        val normalized = normalizeTransactionMode(value)
        return current.copy(
            transactionMode = normalized,
            executionPolicy = normalizeExecutionPolicy(current.executionPolicy, normalized),
        )
    }

    fun updateMaxRowsPerShardDraft(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        current.copy(maxRowsPerShardDraft = value)

    fun updateQueryTimeoutDraft(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        current.copy(queryTimeoutSecDraft = value)

    fun applyRecentQuery(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        if (value.isBlank()) current else current.copy(draftSql = value)

    fun applyFavoriteQuery(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        if (value.isBlank()) current else current.copy(draftSql = value)

    fun rememberFavoriteQuery(current: SqlConsolePageState): SqlConsolePageState {
        val sql = current.draftSql.trim()
        if (sql.isBlank()) {
            return current.copy(errorMessage = "Сначала введи SQL-запрос.", successMessage = null)
        }
        return current.copy(
            errorMessage = null,
            successMessage = "Запрос добавлен в избранное.",
            favoriteQueries = rememberQuery(current.favoriteQueries, sql, limit = 20),
        )
    }

    fun removeFavoriteQuery(
        current: SqlConsolePageState,
        value: String,
    ): SqlConsolePageState =
        current.copy(
            favoriteQueries = current.favoriteQueries.filterNot { it == value },
            errorMessage = null,
            successMessage = "Запрос убран из избранного.",
        )

    fun clearRecentQueries(current: SqlConsolePageState): SqlConsolePageState =
        current.copy(
            recentQueries = emptyList(),
            errorMessage = null,
            successMessage = "История последних запросов очищена.",
        )

    suspend fun persistState(current: SqlConsolePageState): SqlConsolePageState =
        runCatching {
            api.saveState(current.toPersistedState())
            current
        }.getOrElse {
            current
        }

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

    suspend fun startQuery(current: SqlConsolePageState): SqlConsolePageState {
        val sql = current.draftSql.trim()
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
                    executionPolicy = current.executionPolicy,
                    transactionMode = current.transactionMode,
                ),
            )
            current.copy(
                actionInProgress = null,
                errorMessage = null,
                successMessage = "Запрос запущен.",
                currentExecutionId = started.id,
                currentExecution = SqlConsoleExecutionResponse(
                    id = started.id,
                    status = started.status,
                    startedAt = started.startedAt,
                    cancelRequested = started.cancelRequested,
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

    fun beginAction(
        current: SqlConsolePageState,
        actionName: String,
    ): SqlConsolePageState =
        current.copy(actionInProgress = actionName, errorMessage = null, successMessage = null)
}

private fun SqlConsolePageState.toPersistedState(): SqlConsoleStateUpdate =
    SqlConsoleStateUpdate(
        draftSql = draftSql,
        recentQueries = recentQueries,
        favoriteQueries = favoriteQueries,
        selectedSourceNames = selectedSourceNames,
        pageSize = pageSize,
        strictSafetyEnabled = strictSafetyEnabled,
        executionPolicy = executionPolicy,
        transactionMode = transactionMode,
    )

private fun rememberQuery(
    current: List<String>,
    sql: String,
    limit: Int,
): List<String> =
    listOf(sql) + current.filterNot { it == sql }.take(limit - 1)

private fun normalizePageSize(value: Int): Int =
    when (value) {
        25, 50, 100 -> value
        else -> 50
    }

private fun normalizeExecutionPolicy(
    value: String,
    transactionMode: String,
): String {
    val normalized = when (value.uppercase()) {
        "CONTINUE_ON_ERROR" -> "CONTINUE_ON_ERROR"
        else -> "STOP_ON_FIRST_ERROR"
    }
    return if (transactionMode == "TRANSACTION_PER_SHARD" && normalized == "CONTINUE_ON_ERROR") {
        "STOP_ON_FIRST_ERROR"
    } else {
        normalized
    }
}

private fun normalizeTransactionMode(value: String): String =
    when (value.uppercase()) {
        "TRANSACTION_PER_SHARD" -> "TRANSACTION_PER_SHARD"
        else -> "AUTO_COMMIT"
    }
