package com.sbrf.lt.platform.composeui.module_runs

class ModuleRunsStore(
    private val api: ModuleRunsApi,
) {
    suspend fun load(
        route: ModuleRunsRouteState,
        historyLimit: Int = 20,
    ): ModuleRunsPageState {
        return runCatching {
            val session = api.loadSession(route.storage, route.moduleId)
            val history = api.loadHistory(route.storage, route.moduleId, historyLimit)
            val selectedRunId = resolveSelectedRunId(history, null)
            val details = selectedRunId?.let { api.loadRunDetails(route.storage, route.moduleId, it) }
            ModuleRunsPageState(
                loading = false,
                errorMessage = null,
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
            val details = api.loadRunDetails(route.storage, route.moduleId, runId)
            current.copy(
                loading = false,
                errorMessage = null,
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
    ): ModuleRunsPageState {
        return runCatching {
            val history = api.loadHistory(route.storage, route.moduleId, current.historyLimit)
            val selectedRunId = resolveSelectedRunId(history, current.selectedRunId)
            val details = selectedRunId?.let { api.loadRunDetails(route.storage, route.moduleId, it) }
            current.copy(
                loading = false,
                errorMessage = null,
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

    suspend fun updateHistoryLimit(
        current: ModuleRunsPageState,
        route: ModuleRunsRouteState,
        historyLimit: Int,
    ): ModuleRunsPageState {
        return load(route, historyLimit).copy(
            historyFilter = current.historyFilter,
            searchQuery = current.searchQuery,
        )
    }

    fun updateHistoryFilter(
        current: ModuleRunsPageState,
        historyFilter: ModuleRunsHistoryFilter,
    ): ModuleRunsPageState =
        current.copy(historyFilter = historyFilter)

    fun updateSearchQuery(
        current: ModuleRunsPageState,
        searchQuery: String,
    ): ModuleRunsPageState =
        current.copy(searchQuery = searchQuery)

    fun startLoading(current: ModuleRunsPageState): ModuleRunsPageState =
        current.copy(loading = true, errorMessage = null)

    private fun resolveSelectedRunId(
        history: ModuleRunHistoryResponse,
        currentSelectedRunId: String?,
    ): String? =
        when {
            currentSelectedRunId != null && history.runs.any { it.runId == currentSelectedRunId } -> currentSelectedRunId
            !history.activeRunId.isNullOrBlank() -> history.activeRunId
            else -> history.runs.firstOrNull()?.runId
        }
}
