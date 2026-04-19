package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.ConfigFormParseRequest
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

internal fun Route.registerCommonFilesModuleRoutes(
    context: UiServerContext,
    mapper: ObjectMapper,
) {
    val logger = LoggerFactory.getLogger("ConfigFormRoutes")

    get("/api/modules") {
        call.respond(context.filesModuleBackend.listModules())
    }

    get("/api/modules/catalog") {
        call.respond(context.buildFilesModulesCatalogResponse())
    }

    get("/api/modules/{id}") {
        call.respond(context.filesModuleBackend.loadModule(call.requireRouteParam("id")))
    }

    post("/api/modules/{id}/save") {
        call.respond(
            context.saveFilesModule(
                moduleId = call.requireRouteParam("id"),
                request = call.receive<SaveModuleRequest>(),
            ),
        )
    }

    post("/api/config-form/parse") {
        val request = call.receive<ConfigFormParseRequest>()
        call.respond(context.configFormService.parse(request.configText))
    }

    post("/api/config-form/update") {
        val payload = call.receiveText()
        call.respond(context.applyCommonConfigFormUpdate(mapper, payload, logger))
    }
}
