package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleStoreLoadingSupport(
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
        val persistedState = persistedStateResult.getOrDefault(defaultSqlConsoleStateSnapshot())
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
}
