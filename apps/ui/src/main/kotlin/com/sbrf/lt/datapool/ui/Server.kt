package com.sbrf.lt.datapool.ui

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.collect
import org.slf4j.event.Level

fun startUiServer(port: Int = 8080) {
    embeddedServer(Netty, port = port) {
        uiModule()
    }.start(wait = true)
}

fun Application.uiModule() {
    val moduleRegistry = ModuleRegistry()
    val runManager = RunManager(moduleRegistry = moduleRegistry)
    val mapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    install(CallLogging) {
        level = Level.INFO
    }
    install(WebSockets)
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Неизвестная ошибка")))
        }
    }

    routing {
        get("/") {
            val content = javaClass.classLoader.getResourceAsStream("static/index.html")
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Ресурс static/index.html не найден")
            call.respondText(
                content,
                ContentType.Text.Html,
            )
        }
        staticResources("/static", "static")

        get("/api/modules") {
            call.respond(moduleRegistry.listModules().map { mapOf("id" to it.id, "title" to it.title) })
        }

        get("/api/modules/{id}") {
            call.respond(moduleRegistry.loadModuleDetails(requireNotNull(call.parameters["id"])))
        }

        post("/api/modules/{id}/save") {
            moduleRegistry.saveModule(
                requireNotNull(call.parameters["id"]),
                call.receive<SaveModuleRequest>(),
            )
            call.respond(SaveResultResponse("Изменения модуля сохранены."))
        }

        post("/api/runs") {
            call.respond(runManager.startRun(call.receive<StartRunRequest>()))
        }

        get("/api/state") {
            call.respond(runManager.currentState())
        }

        webSocket("/ws") {
            send(Frame.Text(mapper.writeValueAsString(runManager.currentState())))
            runManager.updates().collect { state ->
                send(Frame.Text(mapper.writeValueAsString(state)))
            }
        }
    }
}
