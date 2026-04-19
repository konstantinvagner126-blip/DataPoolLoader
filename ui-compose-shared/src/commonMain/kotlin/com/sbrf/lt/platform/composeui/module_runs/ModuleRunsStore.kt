package com.sbrf.lt.platform.composeui.module_runs

import com.sbrf.lt.platform.composeui.model.ModuleStoreMode

class ModuleRunsStore(
    private val api: ModuleRunsApi,
) {
    private val loadingSupport = ModuleRunsStoreLoadingSupport(api)

    suspend fun load(
        route: ModuleRunsRouteState,
        historyLimit: Int = 20,
    ): ModuleRunsPageState =
        loadingSupport.load(route, historyLimit)

    suspend fun selectRun(
        current: ModuleRunsPageState,
        route: ModuleRunsRouteState,
        runId: String,
    ): ModuleRunsPageState =
        loadingSupport.selectRun(current, route, runId)

    suspend fun reloadHistory(
        current: ModuleRunsPageState,
        route: ModuleRunsRouteState,
        preferActiveRun: Boolean = false,
    ): ModuleRunsPageState =
        loadingSupport.reloadHistory(current, route, preferActiveRun)

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

}
