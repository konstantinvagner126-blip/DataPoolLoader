package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.slf4j.Logger

internal fun Route.registerDatabaseCleanupRoutes(
    context: UiServerContext,
    mapper: ObjectMapper,
    logger: Logger,
) {
    get("/api/db/run-history/cleanup/preview") {
        val disableSafeguard = context.includeHiddenQueryParam(call.request.queryParameters["disableSafeguard"])
        call.respond(context.previewDatabaseRunHistoryCleanup(disableSafeguard))
    }

    post("/api/db/run-history/cleanup") {
        val payload = call.receiveText()
        val request = context.parseDatabaseRunHistoryCleanupRequest(mapper, payload, logger)
        call.respond(context.executeDatabaseRunHistoryCleanup(request.disableSafeguard))
    }
}
