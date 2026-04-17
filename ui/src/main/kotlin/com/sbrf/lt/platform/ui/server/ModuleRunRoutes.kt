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
        val runtimeContext = context.currentRuntimeContext()
        val storageMode = requireNotNull(call.parameters["storage"])
        val moduleId = requireNotNull(call.parameters["id"])
        val (service, actor) = context.requireRunHistoryService(storageMode, runtimeContext)
        call.respond(service.loadSession(moduleId, actor))
    }

    get("/api/module-runs/{storage}/{id}/runs") {
        val runtimeContext = context.currentRuntimeContext()
        val storageMode = requireNotNull(call.parameters["storage"])
        val moduleId = requireNotNull(call.parameters["id"])
        val limit = context.parseLimit(call.request.queryParameters["limit"])
        val (service, actor) = context.requireRunHistoryService(storageMode, runtimeContext)
        call.respond(service.listRuns(moduleId, actor, limit))
    }

    get("/api/module-runs/{storage}/{id}/runs/{runId}") {
        val runtimeContext = context.currentRuntimeContext()
        val storageMode = requireNotNull(call.parameters["storage"])
        val moduleId = requireNotNull(call.parameters["id"])
        val runId = requireNotNull(call.parameters["runId"])
        val (service, actor) = context.requireRunHistoryService(storageMode, runtimeContext)
        call.respond(service.loadRunDetails(moduleId, runId, actor))
    }
}
