package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.ConfigFormParseRequest
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.model.StartRunRequest
import com.sbrf.lt.platform.ui.model.UiRuntimeModeUpdateRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.collect
import org.slf4j.LoggerFactory

/**
 * Общие UI API: runtime mode, файловые модули, config-form, credentials и websocket.
 */
internal fun Route.registerCommonRoutes(
    context: UiServerContext,
    mapper: ObjectMapper,
) {
    val logger = LoggerFactory.getLogger("ConfigFormRoutes")
    get("/api/modules") {
        call.respond(context.filesModuleBackend.listModules())
    }

    get("/api/ui/runtime-context") {
        call.respond(context.currentRuntimeContext())
    }

    post("/api/ui/runtime-mode") {
        call.respond(context.updateCommonRuntimeMode(call.receive<UiRuntimeModeUpdateRequest>()))
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

    post("/api/runs") {
        call.respond(context.filesRunService.startRun(call.receive<StartRunRequest>()))
    }

    get("/api/state") {
        call.respond(context.filesRunService.currentState())
    }

    get("/api/credentials") {
        call.respond(context.filesRunService.currentCredentialsStatus())
    }

    get("/api/run-history/cleanup/preview") {
        val runtimeContext = context.currentRuntimeContext()
        val disableSafeguard = context.includeHiddenQueryParam(call.request.queryParameters["disableSafeguard"])
        call.respond(context.previewCommonRunHistoryCleanup(runtimeContext, disableSafeguard))
    }

    post("/api/run-history/cleanup") {
        val runtimeContext = context.currentRuntimeContext()
        val payload = call.receiveText()
        val request = context.parseCommonRunHistoryCleanupRequest(mapper, payload)
        call.respond(context.executeCommonRunHistoryCleanup(runtimeContext, request.disableSafeguard))
    }

    get("/api/output-retention/preview") {
        val runtimeContext = context.currentRuntimeContext()
        val disableSafeguard = context.includeHiddenQueryParam(call.request.queryParameters["disableSafeguard"])
        call.respond(context.previewCommonOutputRetention(runtimeContext, disableSafeguard))
    }

    post("/api/output-retention") {
        val runtimeContext = context.currentRuntimeContext()
        val payload = call.receiveText()
        val request = context.parseCommonOutputRetentionRequest(mapper, payload)
        call.respond(context.executeCommonOutputRetention(runtimeContext, request.disableSafeguard))
    }

    post("/api/credentials/upload") {
        val upload = call.readUploadedCredentialsPayload()
        call.respond(context.filesRunService.uploadCredentials(upload.fileName, upload.content))
    }

    webSocket("/ws") {
        send(Frame.Text(mapper.writeValueAsString(context.filesRunService.currentState())))
        context.filesRunService.updates().collect { state ->
            send(Frame.Text(mapper.writeValueAsString(state)))
        }
    }
}
