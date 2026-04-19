package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.CreateDbModuleRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post

internal fun Route.registerDatabaseModuleRegistryMutationRoutes(context: UiServerContext) {
    post("/api/db/modules") {
        val runtimeContext = context.requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
        val store = context.requireDatabaseRouteStore(runtimeContext)
        val actor = context.requireDatabaseRouteActor(runtimeContext)
        val request = call.receive<CreateDbModuleRequest>()
        val result = store.createModule(
            moduleCode = request.moduleCode,
            actorId = actor.actorId,
            actorSource = actor.actorSource,
            actorDisplayName = actor.actorDisplayName,
            request = com.sbrf.lt.platform.ui.module.CreateModuleRequest(
                title = request.title,
                description = request.description,
                tags = request.tags,
                configText = request.configText,
                hiddenFromUi = request.hiddenFromUi,
            ),
        )
        call.respond(
            mapOf(
                "message" to "Модуль '${result.moduleCode}' создан в базе данных.",
                "moduleId" to result.moduleId,
                "moduleCode" to result.moduleCode,
                "revisionId" to result.revisionId,
                "workingCopyId" to result.workingCopyId,
            ),
        )
    }

    delete("/api/db/modules/{id}") {
        val routeContext = context.requireDatabaseMutableModuleRouteContext(call)
        val store = context.requireDatabaseRouteStore(routeContext.runtimeContext)
        val result = store.deleteModule(
            moduleCode = routeContext.moduleCode,
            actorId = routeContext.actor.actorId,
        )
        call.respond(
            mapOf(
                "message" to "Модуль '${result.moduleCode}' удалён из базы данных.",
                "moduleCode" to result.moduleCode,
                "deletedBy" to result.deletedBy,
            ),
        )
    }
}
