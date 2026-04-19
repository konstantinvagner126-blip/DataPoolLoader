package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.SyncSelectedModulesRequest
import com.sbrf.lt.platform.ui.sync.ModuleSyncRunNotFoundException

internal fun UiServerContext.listDatabaseSyncRuns(limit: Int) =
    requireDatabaseSyncRouteContext().syncService.listSyncRuns(limit)

internal fun UiServerContext.loadDatabaseSyncRunDetailsOrThrow(syncRunId: String) =
    requireDatabaseSyncRouteContext().syncService.loadSyncRunDetails(syncRunId)
        ?: throw ModuleSyncRunNotFoundException(syncRunId)

internal fun UiServerContext.syncOneDatabaseModuleFromFiles(request: com.sbrf.lt.platform.ui.model.SyncOneModuleRequest) =
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
