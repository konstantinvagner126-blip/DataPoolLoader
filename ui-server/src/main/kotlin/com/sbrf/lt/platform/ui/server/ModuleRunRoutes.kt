package com.sbrf.lt.platform.ui.server

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Общий API экрана `История и результаты` для `FILES` и `DATABASE`.
 */
internal fun Route.registerModuleRunRoutes(context: UiServerContext) {
    get("/api/module-runs/{storage}/{id}") {
        val routeContext = context.requireModuleRunRouteContext(call)
        call.respond(routeContext.service.loadSession(routeContext.moduleId, routeContext.actor))
    }

    get("/api/module-runs/{storage}/{id}/runs") {
        val routeContext = context.requireModuleRunRouteContext(call)
        val limit = context.parseLimit(call.request.queryParameters["limit"])
        call.respond(routeContext.service.listRuns(routeContext.moduleId, routeContext.actor, limit))
    }

    get("/api/module-runs/{storage}/{id}/runs/{runId}") {
        val routeContext = context.requireModuleRunDetailsRouteContext(call)
        call.respond(routeContext.service.loadRunDetails(routeContext.moduleId, routeContext.runId, routeContext.actor))
    }
}
