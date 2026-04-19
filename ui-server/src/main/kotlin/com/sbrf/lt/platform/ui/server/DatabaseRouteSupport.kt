package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.module.sync.ModuleSyncService
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.ui.model.toDiagnosticsResponse
import com.sbrf.lt.platform.ui.module.DatabaseModuleRegistryOperations
import com.sbrf.lt.platform.ui.module.backend.DatabaseModuleBackend
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunOperations
import com.sbrf.lt.platform.ui.run.DatabaseRunHistoryCleanupService

internal data class DatabaseRouteActorContext(
    val actorId: String,
    val actorSource: String,
    val actorDisplayName: String?,
)

internal fun UiServerContext.requireDatabaseRuntimeContext(
    requireMaintenanceInactive: Boolean = false,
): UiRuntimeContext {
    val runtimeContext = currentRuntimeContext()
    if (requireMaintenanceInactive) {
        requireDatabaseMaintenanceIsInactive()
    }
    requireDatabaseMode(runtimeContext)
    return runtimeContext
}

internal fun UiServerContext.requireDatabaseRouteCleanupService(runtimeContext: UiRuntimeContext): DatabaseRunHistoryCleanupService {
    requireDatabaseMaintenanceIsInactive()
    requireDatabaseMode(runtimeContext)
    return requireNotNull(currentDatabaseRunHistoryCleanupService()) {
        "Сервис cleanup истории запусков для режима базы данных не настроен."
    }
}

internal fun UiServerContext.requireDatabaseRouteSyncService(runtimeContext: UiRuntimeContext): ModuleSyncService {
    requireDatabaseMode(runtimeContext)
    return requireNotNull(currentModuleSyncService()) {
        "Сервис импорта модулей в базу данных не настроен."
    }
}

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

internal fun UiServerContext.requireDatabaseRouteSyncActor(runtimeContext: UiRuntimeContext): DatabaseRouteActorContext =
    DatabaseRouteActorContext(
        actorId = requireDatabaseActorId(runtimeContext),
        actorSource = runtimeContext.actor.actorSource ?: "OS_LOGIN",
        actorDisplayName = runtimeContext.actor.actorDisplayName,
    )

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
