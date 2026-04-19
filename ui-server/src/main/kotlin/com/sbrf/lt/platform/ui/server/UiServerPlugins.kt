package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.error.UiEntityNotFoundException
import com.sbrf.lt.platform.ui.error.UiStateConflictException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.websocket.WebSockets
import java.util.concurrent.CancellationException
import org.slf4j.event.Level
import org.slf4j.Logger

internal fun Application.installUiServerPlugins(logger: Logger) {
    install(CallLogging) {
        level = Level.INFO
    }
    install(WebSockets)
    install(ContentNegotiation) {
        jackson {
            applyUiServerDefaults()
        }
    }
    install(StatusPages) {
        exception<CancellationException> { _, cause ->
            throw cause
        }
        exception<UiEntityNotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, mapOf("error" to cause.message.orEmpty()))
        }
        exception<UiStateConflictException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, mapOf("error" to cause.message.orEmpty()))
        }
        exception<UiHttpException> { call, cause ->
            call.respond(cause.statusCode, mapOf("error" to cause.message))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Некорректный запрос")))
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled server error for path {}", call.request.path(), cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Внутренняя ошибка сервера."),
            )
        }
    }
}
