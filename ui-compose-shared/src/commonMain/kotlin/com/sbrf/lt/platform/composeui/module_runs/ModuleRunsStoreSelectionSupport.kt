package com.sbrf.lt.platform.composeui.module_runs

internal class ModuleRunsStoreSelectionSupport(
    private val api: ModuleRunsApi,
) {
    suspend fun loadSelectedRunDetails(
        route: ModuleRunsRouteState,
        runId: String?,
    ): ModuleRunDetailsResponse? =
        runId?.let { api.loadRunDetails(route.storage, route.moduleId, it) }
}

internal fun resolveSelectedRunId(
    history: ModuleRunHistoryResponse,
    currentSelectedRunId: String?,
    preferActiveRun: Boolean = false,
): String? =
    when {
        preferActiveRun && !history.activeRunId.isNullOrBlank() -> history.activeRunId
        currentSelectedRunId != null && history.runs.any { it.runId == currentSelectedRunId } -> currentSelectedRunId
        !history.activeRunId.isNullOrBlank() -> history.activeRunId
        else -> history.runs.firstOrNull()?.runId
    }
