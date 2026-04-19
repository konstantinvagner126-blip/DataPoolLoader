package com.sbrf.lt.platform.ui.server

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.registerDatabaseModuleCatalogRoutes(context: UiServerContext) {
    get("/api/db/modules/catalog") {
        val includeHidden = context.includeHiddenQueryParam(call.request.queryParameters["includeHidden"])
        call.respond(context.buildDatabaseModulesCatalogResponse(includeHidden))
    }

    get("/api/db/modules/{id}") {
        val routeContext = context.requireDatabaseModuleRouteContext(call)
        val backend = context.requireDatabaseRouteBackend(routeContext.runtimeContext)
        call.respond(
            backend.loadModule(
                moduleId = routeContext.moduleCode,
                actor = context.requireDatabaseActor(routeContext.runtimeContext),
            ),
        )
    }
}
