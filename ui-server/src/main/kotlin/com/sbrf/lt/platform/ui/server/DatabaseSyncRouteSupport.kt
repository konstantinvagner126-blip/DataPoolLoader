package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.module.sync.ModuleSyncService
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.model.SyncSelectedModulesRequest
import com.sbrf.lt.platform.ui.sync.ModuleSyncRunNotFoundException
import org.slf4j.Logger

internal data class DatabaseSyncRouteContext(
    val runtimeContext: UiRuntimeContext,
    val syncService: ModuleSyncService,
    val actor: DatabaseRouteActorContext,
)

internal fun UiServerContext.requireDatabaseRouteSyncService(runtimeContext: UiRuntimeContext): ModuleSyncService {
    requireDatabaseMode(runtimeContext)
    return requireNotNull(currentModuleSyncService()) {
        "Сервис импорта модулей в базу данных не настроен."
    }
}

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

internal fun UiServerContext.parseSyncSelectedModulesRequest(
    mapper: ObjectMapper,
    payload: String,
    logger: Logger,
): SyncSelectedModulesRequest =
    try {
        mapper.readValue(payload, SyncSelectedModulesRequest::class.java)
    } catch (error: Exception) {
        logger.warn("Некорректный payload для /api/db/sync/selected: {}", payload.take(4_000), error)
        badRequest("Некорректные данные для выборочной синхронизации.")
    }

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
