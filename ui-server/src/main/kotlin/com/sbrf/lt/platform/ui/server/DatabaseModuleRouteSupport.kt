package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.ui.model.toDiagnosticsResponse
import com.sbrf.lt.platform.ui.module.DatabaseModuleRegistryOperations
import com.sbrf.lt.platform.ui.module.backend.DatabaseModuleBackend
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunOperations
import io.ktor.server.application.ApplicationCall

internal data class DatabaseRouteActorContext(
    val actorId: String,
    val actorSource: String,
    val actorDisplayName: String?,
)

internal data class DatabaseModuleRouteContext(
    val moduleCode: String,
    val runtimeContext: UiRuntimeContext,
)

internal data class DatabaseMutableModuleRouteContext(
    val moduleCode: String,
    val runtimeContext: UiRuntimeContext,
    val actor: DatabaseRouteActorContext,
)

internal fun UiServerContext.requireDatabaseRouteBackend(runtimeContext: UiRuntimeContext): DatabaseModuleBackend {
    requireDatabaseMaintenanceIsInactive()
    requireDatabaseMode(runtimeContext)
    return requireNotNull(currentDatabaseModuleBackend()) {
        "Сервис работы с модулями из базы данных не настроен."
    }
}

internal fun UiServerContext.requireDatabaseRouteStore(runtimeContext: UiRuntimeContext): DatabaseModuleRegistryOperations {
    requireDatabaseMaintenanceIsInactive()
    requireDatabaseMode(runtimeContext)
    return requireNotNull(currentDatabaseModuleStore()) {
        "Хранилище модулей из базы данных не настроено."
    }
}

internal fun UiServerContext.requireDatabaseRouteRunService(runtimeContext: UiRuntimeContext): DatabaseModuleRunOperations {
    requireDatabaseMaintenanceIsInactive()
    requireDatabaseMode(runtimeContext)
    return requireNotNull(currentDatabaseModuleRunService()) {
        "Сервис истории запусков для режима базы данных не настроен."
    }
}

internal fun UiServerContext.requireDatabaseRouteActor(runtimeContext: UiRuntimeContext): DatabaseRouteActorContext =
    DatabaseRouteActorContext(
        actorId = requireDatabaseActorId(runtimeContext),
        actorSource = requireDatabaseActorSource(runtimeContext),
        actorDisplayName = runtimeContext.actor.actorDisplayName,
    )

internal fun UiServerContext.requireDatabaseModuleRouteContext(call: ApplicationCall): DatabaseModuleRouteContext {
    val runtimeContext = requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
    return DatabaseModuleRouteContext(
        moduleCode = call.requireRouteParam("id"),
        runtimeContext = runtimeContext,
    )
}

internal fun UiServerContext.requireDatabaseMutableModuleRouteContext(call: ApplicationCall): DatabaseMutableModuleRouteContext {
    val routeContext = requireDatabaseModuleRouteContext(call)
    requireDatabaseModuleIsNotSyncing(routeContext.moduleCode)
    return DatabaseMutableModuleRouteContext(
        moduleCode = routeContext.moduleCode,
        runtimeContext = routeContext.runtimeContext,
        actor = requireDatabaseRouteActor(routeContext.runtimeContext),
    )
}

internal fun UiServerContext.buildDatabaseModulesCatalogResponse(includeHidden: Boolean): DatabaseModulesCatalogResponse {
    requireDatabaseMaintenanceIsInactive()
    val runtimeContext = currentRuntimeContext()
    val store = currentDatabaseModuleStore()
    val modules = if (runtimeContext.effectiveMode == UiModuleStoreMode.DATABASE && store != null) {
        val activeModuleCodes = runCatching {
            currentDatabaseModuleRunService()?.activeModuleCodes().orEmpty()
        }.getOrDefault(emptySet())
        currentDatabaseModuleBackend()
            ?.listModules(includeHidden = includeHidden)
            ?.map { module ->
                module.copy(hasActiveRun = module.id in activeModuleCodes)
            }
            .orEmpty()
    } else {
        emptyList()
    }
    return DatabaseModulesCatalogResponse(
        runtimeContext = runtimeContext,
        diagnostics = modules.toDiagnosticsResponse(),
        modules = modules,
    )
}
