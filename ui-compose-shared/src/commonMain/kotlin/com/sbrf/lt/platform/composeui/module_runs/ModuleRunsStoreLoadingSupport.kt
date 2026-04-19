package com.sbrf.lt.platform.composeui.module_runs

import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeContext

internal class ModuleRunsStoreLoadingSupport(
    private val api: ModuleRunsApi,
) {
    suspend fun load(
        route: ModuleRunsRouteState,
        historyLimit: Int = 20,
    ): ModuleRunsPageState {
        return runCatching {
            val runtimeContext = api.loadRuntimeContext()
            if (route.storage == "database" && runtimeContext.effectiveMode != ModuleStoreMode.DATABASE) {
                return ModuleRunsPageState(
                    loading = false,
                    errorMessage = runtimeContext.fallbackReason
                        ?: "Режим базы данных сейчас недоступен.",
                    runtimeContext = runtimeContext,
                    historyLimit = historyLimit,
                )
            }
            val session = api.loadSession(route.storage, route.moduleId)
            val history = api.loadHistory(route.storage, route.moduleId, historyLimit)
            val selectedRunId = resolveSelectedRunId(history, null)
            val details = selectedRunId?.let { api.loadRunDetails(route.storage, route.moduleId, it) }
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
            val runtimeContext = loadDatabaseRuntimeContext(route) ?: current.runtimeContext
            if (route.storage == "database" && runtimeContext?.effectiveMode != ModuleStoreMode.DATABASE) {
                return buildDatabaseFallbackState(
                    current = current,
                    runtimeContext = runtimeContext,
                )
            }
            val details = api.loadRunDetails(route.storage, route.moduleId, runId)
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
            val runtimeContext = loadDatabaseRuntimeContext(route) ?: current.runtimeContext
            if (route.storage == "database" && runtimeContext?.effectiveMode != ModuleStoreMode.DATABASE) {
                return buildDatabaseFallbackState(
                    current = current,
                    runtimeContext = runtimeContext,
                )
            }
            val history = api.loadHistory(route.storage, route.moduleId, current.historyLimit)
            val selectedRunId = resolveSelectedRunId(history, current.selectedRunId, preferActiveRun)
            val details = selectedRunId?.let { api.loadRunDetails(route.storage, route.moduleId, it) }
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

    private suspend fun loadDatabaseRuntimeContext(route: ModuleRunsRouteState) =
        if (route.storage == "database") {
            api.loadRuntimeContext()
        } else {
            null
        }
}

internal fun buildDatabaseFallbackState(
    current: ModuleRunsPageState,
    runtimeContext: RuntimeContext?,
): ModuleRunsPageState =
    current.copy(
        loading = false,
        errorMessage = runtimeContext?.fallbackReason ?: "Режим базы данных сейчас недоступен.",
        runtimeContext = runtimeContext,
        history = null,
        selectedRunId = null,
        selectedRunDetails = null,
    )

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
