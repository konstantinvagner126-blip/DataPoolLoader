package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.StartRunRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.collect

internal fun Route.registerCommonFilesRunRoutes(
    context: UiServerContext,
    mapper: ObjectMapper,
) {
    post("/api/runs") {
        call.respond(context.filesRunService.startRun(call.receive<StartRunRequest>()))
    }

    get("/api/state") {
        call.respond(context.filesRunService.currentState())
    }

    get("/api/credentials") {
        call.respond(context.filesRunService.currentCredentialsStatus())
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
