package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleStoreLoadingSupport(
    private val api: SqlConsoleApi,
) {
    suspend fun load(workspaceId: String? = null): SqlConsolePageState {
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

        val persistedStateResult = runCatching { api.loadState(workspaceId) }
        val persistedState = persistedStateResult.getOrDefault(defaultSqlConsoleStateSnapshot())
        val executionHistory = runCatching { api.loadExecutionHistory(workspaceId).entries }.getOrDefault(emptyList())
        val allSourceNames = info.sourceCatalogNames()
        val selectedSources = persistedState.selectedSourceNames
            .filter { it in allSourceNames }
            .ifEmpty { allSourceNames }
        val sourceSelectionState = restoreSelectedSourceState(
            groups = info.groups,
            selectedGroupNames = persistedState.selectedGroupNames,
            selectedSourceNames = selectedSources,
        )
        val normalizedTransactionMode = normalizeTransactionMode(persistedState.transactionMode)
        return SqlConsolePageState(
            loading = false,
            runtimeContext = runtimeContext,
            info = info,
            draftSql = persistedState.draftSql.ifBlank { "select 1 as check_value" },
            recentQueries = persistedState.recentQueries,
            favoriteQueries = persistedState.favoriteQueries,
            favoriteObjects = persistedState.favoriteObjects,
            executionHistory = executionHistory,
            selectedSourceNames = sourceSelectionState.selectedSourceNames,
            selectedGroupNames = sourceSelectionState.selectedGroupNames,
            manuallyIncludedSourceNames = sourceSelectionState.manuallyIncludedSourceNames,
            manuallyExcludedSourceNames = sourceSelectionState.manuallyExcludedSourceNames,
            pageSize = normalizePageSize(persistedState.pageSize),
            strictSafetyEnabled = persistedState.strictSafetyEnabled,
            transactionMode = normalizedTransactionMode,
            maxRowsPerShardDraft = info.maxRowsPerShard.toString(),
            queryTimeoutSecDraft = info.queryTimeoutSec?.toString().orEmpty(),
            errorMessage = persistedStateResult.exceptionOrNull()?.message,
        )
    }

    suspend fun persistState(current: SqlConsolePageState): SqlConsolePageState =
        runCatching {
            api.saveState(current.toPersistedState())
            current
        }.getOrElse {
            current
        }

    suspend fun persistState(
        current: SqlConsolePageState,
        workspaceId: String,
    ): SqlConsolePageState =
        runCatching {
            api.saveState(current.toPersistedState(), workspaceId = workspaceId)
            current
        }.getOrElse {
            current
        }

    suspend fun refreshExecutionHistory(
        current: SqlConsolePageState,
        workspaceId: String,
    ): SqlConsolePageState =
        runCatching {
            current.copy(executionHistory = api.loadExecutionHistory(workspaceId).entries)
        }.getOrElse {
            current
        }
}
