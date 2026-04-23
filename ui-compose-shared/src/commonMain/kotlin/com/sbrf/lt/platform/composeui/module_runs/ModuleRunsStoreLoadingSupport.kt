package com.sbrf.lt.platform.composeui.module_runs

internal class ModuleRunsStoreLoadingSupport(
    private val api: ModuleRunsApi,
) {
    private val runtimeSupport = ModuleRunsStoreRuntimeSupport(api)
    private val selectionSupport = ModuleRunsStoreSelectionSupport(api)

    suspend fun load(
        route: ModuleRunsRouteState,
        historyLimit: Int = 20,
    ): ModuleRunsPageState {
        return runCatching {
            val runtimeContext = runtimeSupport.loadInitialRuntimeContext()
            if (runtimeSupport.requiresDatabaseFallback(route, runtimeContext)) {
                return runtimeSupport.buildInitialDatabaseFallbackState(runtimeContext, historyLimit)
            }
            val session = api.loadSession(route.storage, route.moduleId)
            val history = api.loadHistory(route.storage, route.moduleId, historyLimit)
            val selectedRunId = resolveSelectedRunId(history, null)
            val details = selectionSupport.loadSelectedRunDetails(route, selectedRunId)
            ModuleRunsPageState(
                loading = false,
                errorMessage = null,
                runtimeContext = runtimeContext,
                session = session,
                history = history,
                selectedRunId = selectedRunId,
                selectedRunDetails = details,
                historyLimit = historyLimit,
            )
        }.getOrElse { error ->
            ModuleRunsPageState(
                loading = false,
                errorMessage = error.message ?: "Не удалось загрузить историю запусков.",
                historyLimit = historyLimit,
            )
        }
    }

    suspend fun selectRun(
        current: ModuleRunsPageState,
        route: ModuleRunsRouteState,
        runId: String,
    ): ModuleRunsPageState {
        return runCatching {
            val runtimeContext = runtimeSupport.loadRouteRuntimeContext(route, current.runtimeContext)
            if (runtimeSupport.requiresDatabaseFallback(route, runtimeContext)) {
                return buildDatabaseFallbackState(
                    current = current,
                    runtimeContext = runtimeContext,
                )
            }
            val details = selectionSupport.loadSelectedRunDetails(route, runId)
            current.copy(
                loading = false,
                errorMessage = null,
                runtimeContext = runtimeContext,
                selectedRunId = runId,
                selectedRunDetails = details,
            )
        }.getOrElse { error ->
            current.copy(
                loading = false,
                errorMessage = error.message ?: "Не удалось загрузить подробности запуска.",
            )
        }
    }

    suspend fun reloadHistory(
        current: ModuleRunsPageState,
        route: ModuleRunsRouteState,
        preferActiveRun: Boolean = false,
    ): ModuleRunsPageState {
        return runCatching {
            val runtimeContext = runtimeSupport.loadRouteRuntimeContext(route, current.runtimeContext)
            if (runtimeSupport.requiresDatabaseFallback(route, runtimeContext)) {
                return buildDatabaseFallbackState(
                    current = current,
                    runtimeContext = runtimeContext,
                )
            }
            val history = api.loadHistory(route.storage, route.moduleId, current.historyLimit)
            val selectedRunId = resolveSelectedRunId(history, current.selectedRunId, preferActiveRun)
            val details = selectionSupport.loadSelectedRunDetails(route, selectedRunId)
            current.copy(
                loading = false,
                errorMessage = null,
                runtimeContext = runtimeContext,
                history = history,
                selectedRunId = selectedRunId,
                selectedRunDetails = details,
            )
        }.getOrElse { error ->
            current.copy(
                loading = false,
                errorMessage = error.message ?: "Не удалось обновить историю запусков.",
            )
        }
    }
}
