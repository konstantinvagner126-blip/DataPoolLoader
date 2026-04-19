package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiRuntimeContext
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
