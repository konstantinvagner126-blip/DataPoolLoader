package com.sbrf.lt.platform.composeui.module_runs

import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeContext

internal class ModuleRunsStoreRuntimeSupport(
    private val api: ModuleRunsApi,
) {
    suspend fun loadInitialRuntimeContext(): RuntimeContext = api.loadRuntimeContext()

    suspend fun loadRouteRuntimeContext(
        route: ModuleRunsRouteState,
        current: RuntimeContext? = null,
    ): RuntimeContext? =
        if (route.storage == "database") {
            api.loadRuntimeContext()
        } else {
            current
        }

    fun requiresDatabaseFallback(
        route: ModuleRunsRouteState,
        runtimeContext: RuntimeContext?,
    ): Boolean =
        route.storage == "database" && runtimeContext?.effectiveMode != ModuleStoreMode.DATABASE

    fun buildInitialDatabaseFallbackState(
        runtimeContext: RuntimeContext,
        historyLimit: Int,
    ): ModuleRunsPageState =
        ModuleRunsPageState(
            loading = false,
            errorMessage = runtimeContext.fallbackReason
                ?: "Режим базы данных сейчас недоступен.",
            runtimeContext = runtimeContext,
            historyLimit = historyLimit,
        )
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
