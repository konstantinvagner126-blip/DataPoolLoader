package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.module.sync.ModuleSyncService
import com.sbrf.lt.platform.ui.config.UiRuntimeContext

internal data class DatabaseSyncRequestContext(
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

internal fun UiServerContext.requireDatabaseSyncRouteContext(): DatabaseSyncRequestContext {
    val runtimeContext = requireDatabaseRuntimeContext()
    return DatabaseSyncRequestContext(
        runtimeContext = runtimeContext,
        syncService = requireDatabaseRouteSyncService(runtimeContext),
        actor = requireDatabaseRouteSyncActor(runtimeContext),
    )
}
