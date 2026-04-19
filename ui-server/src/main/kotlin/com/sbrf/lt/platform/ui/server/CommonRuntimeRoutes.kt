package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.UiRuntimeModeUpdateRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

internal fun Route.registerCommonRuntimeRoutes(context: UiServerContext) {
    get("/api/ui/runtime-context") {
        call.respond(context.currentRuntimeContext())
    }

    post("/api/ui/runtime-mode") {
        call.respond(context.updateCommonRuntimeMode(call.receive<UiRuntimeModeUpdateRequest>()))
    }
}
