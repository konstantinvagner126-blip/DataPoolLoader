package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.DatabaseRunStartRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

internal fun Route.registerDatabaseModuleRunRoutes(context: UiServerContext) {
    get("/api/db/modules/{id}/runs") {
        val routeContext = context.requireDatabaseModuleRouteContext(call)
        val runService = context.requireDatabaseRouteRunService(routeContext.runtimeContext)
        call.respond(runService.listRuns(routeContext.moduleCode))
    }

    get("/api/db/modules/{id}/runs/{runId}") {
        val routeContext = context.requireDatabaseModuleRouteContext(call)
        val runService = context.requireDatabaseRouteRunService(routeContext.runtimeContext)
        call.respond(
            runService.loadRunDetails(
                moduleCode = routeContext.moduleCode,
                runId = call.requireRouteParam("runId"),
            ),
        )
    }

    post("/api/db/modules/{id}/run") {
        val routeContext = context.requireDatabaseMutableModuleRouteContext(call)
        val runService = context.requireDatabaseRouteRunService(routeContext.runtimeContext)
        call.receive<DatabaseRunStartRequest>()
        call.respond(
            runService.startRun(
                moduleCode = routeContext.moduleCode,
                actorId = routeContext.actor.actorId,
                actorSource = routeContext.actor.actorSource,
                actorDisplayName = routeContext.actor.actorDisplayName,
            ),
        )
    }
}
