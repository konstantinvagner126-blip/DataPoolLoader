package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.module.sync.ModuleSyncService
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse
import com.sbrf.lt.platform.ui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.SyncOneModuleRequest
import com.sbrf.lt.platform.ui.model.SyncSelectedModulesRequest
import com.sbrf.lt.platform.ui.model.toDiagnosticsResponse
import com.sbrf.lt.platform.ui.module.DatabaseModuleRegistryOperations
import com.sbrf.lt.platform.ui.module.backend.DatabaseModuleBackend
import com.sbrf.lt.platform.ui.run.DatabaseModuleRunOperations
import com.sbrf.lt.platform.ui.run.DatabaseRunHistoryCleanupService
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

internal data class DatabaseSyncRouteContext(
    val runtimeContext: UiRuntimeContext,
    val syncService: ModuleSyncService,
    val actor: DatabaseRouteActorContext,
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

internal fun UiServerContext.requireDatabaseSyncRouteContext(): DatabaseSyncRouteContext {
    val runtimeContext = requireDatabaseRuntimeContext()
    return DatabaseSyncRouteContext(
        runtimeContext = runtimeContext,
        syncService = requireDatabaseRouteSyncService(runtimeContext),
        actor = requireDatabaseRouteSyncActor(runtimeContext),
    )
}

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

internal fun UiServerContext.previewDatabaseRunHistoryCleanup(
    disableSafeguard: Boolean,
): DatabaseRunHistoryCleanupPreviewResponse {
    val runtimeContext = requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
    return requireDatabaseRouteCleanupService(runtimeContext).previewCleanup(disableSafeguard = disableSafeguard)
}

internal fun UiServerContext.executeDatabaseRunHistoryCleanup(
    disableSafeguard: Boolean,
): DatabaseRunHistoryCleanupResultResponse {
    val runtimeContext = requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
    return requireDatabaseRouteCleanupService(runtimeContext).executeCleanup(disableSafeguard = disableSafeguard)
}

internal fun UiServerContext.listDatabaseSyncRuns(limit: Int) =
    requireDatabaseSyncRouteContext().syncService.listSyncRuns(limit)

internal fun UiServerContext.loadDatabaseSyncRunDetails(syncRunId: String) =
    requireDatabaseSyncRouteContext().syncService.loadSyncRunDetails(syncRunId)

internal fun UiServerContext.syncOneDatabaseModuleFromFiles(request: SyncOneModuleRequest) =
    requireDatabaseSyncRouteContext().let { routeContext ->
        routeContext.syncService.syncOneFromFiles(
            moduleCode = request.moduleCode,
            appsRoot = currentAppsRootOrFail(),
            actorId = routeContext.actor.actorId,
            actorSource = routeContext.actor.actorSource,
            actorDisplayName = routeContext.actor.actorDisplayName,
        )
    }

internal fun UiServerContext.syncSelectedDatabaseModulesFromFiles(request: SyncSelectedModulesRequest) =
    requireDatabaseSyncRouteContext().let { routeContext ->
        routeContext.syncService.syncSelectedFromFiles(
            moduleCodes = request.moduleCodes,
            appsRoot = currentAppsRootOrFail(),
            actorId = routeContext.actor.actorId,
            actorSource = routeContext.actor.actorSource,
            actorDisplayName = routeContext.actor.actorDisplayName,
        )
    }

internal fun UiServerContext.syncAllDatabaseModulesFromFiles() =
    requireDatabaseSyncRouteContext().let { routeContext ->
        routeContext.syncService.syncAllFromFiles(
            appsRoot = currentAppsRootOrFail(),
            actorId = routeContext.actor.actorId,
            actorSource = routeContext.actor.actorSource,
            actorDisplayName = routeContext.actor.actorDisplayName,
        )
    }
