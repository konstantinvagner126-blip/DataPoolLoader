package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.model.SaveResultResponse
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

internal fun Route.registerDatabaseModuleWorkingCopyRoutes(context: UiServerContext) {
    post("/api/db/modules/{id}/save") {
        val routeContext = context.requireDatabaseMutableModuleRouteContext(call)
        val backend = context.requireDatabaseRouteBackend(routeContext.runtimeContext)
        backend.saveModule(
            moduleId = routeContext.moduleCode,
            request = call.receive<SaveModuleRequest>(),
            actor = context.requireDatabaseActor(routeContext.runtimeContext),
        )
        call.respond(SaveResultResponse("Изменения сохранены в личный черновик."))
    }

    post("/api/db/modules/{id}/discard-working-copy") {
        val routeContext = context.requireDatabaseMutableModuleRouteContext(call)
        val store = context.requireDatabaseRouteStore(routeContext.runtimeContext)
        store.discardWorkingCopy(
            moduleCode = routeContext.moduleCode,
            actorId = routeContext.actor.actorId,
            actorSource = routeContext.actor.actorSource,
        )
        call.respond(SaveResultResponse("Личный черновик удалён."))
    }

    post("/api/db/modules/{id}/publish") {
        val routeContext = context.requireDatabaseMutableModuleRouteContext(call)
        val store = context.requireDatabaseRouteStore(routeContext.runtimeContext)
        val result = store.publishWorkingCopy(
            moduleCode = routeContext.moduleCode,
            actorId = routeContext.actor.actorId,
            actorSource = routeContext.actor.actorSource,
            actorDisplayName = routeContext.actor.actorDisplayName,
        )
        call.respond(
            mapOf(
                "message" to "Черновик опубликован как ревизия #${result.revisionNo}.",
                "revisionId" to result.revisionId,
                "revisionNo" to result.revisionNo,
                "moduleCode" to result.moduleCode,
            ),
        )
    }
}
