package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleObjectsStoreLoadingSupport(
    private val api: SqlConsoleApi,
) {
    suspend fun load(): SqlConsoleObjectsPageState {
        val runtimeContextResult = runCatching { api.loadRuntimeContext() }
        val runtimeContext = runtimeContextResult.getOrNull()
        if (runtimeContext == null) {
            return SqlConsoleObjectsPageState(
                loading = false,
                errorMessage = runtimeContextResult.exceptionOrNull()?.message
                    ?: "Не удалось загрузить runtime context экрана объектов БД.",
            )
        }
        val infoResult = runCatching { api.loadInfo() }
        val info = infoResult.getOrNull()
        if (info == null) {
            return SqlConsoleObjectsPageState(
                loading = false,
                runtimeContext = runtimeContext,
                errorMessage = infoResult.exceptionOrNull()?.message ?: "Не удалось загрузить конфигурацию SQL-консоли.",
            )
        }
        val persistedState = runCatching { api.loadState() }
            .getOrDefault(defaultSqlConsoleStateSnapshot())
        val selectedSources = persistedState.selectedSourceNames
            .filter { it in info.sourceNames }
            .ifEmpty { info.sourceNames }
        return SqlConsoleObjectsPageState(
            loading = false,
            runtimeContext = runtimeContext,
            info = info,
            persistedState = persistedState,
            selectedSourceNames = selectedSources,
            favoriteObjects = persistedState.favoriteObjects,
        )
    }
}
