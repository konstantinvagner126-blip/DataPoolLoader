package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.SyncOneModuleRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.slf4j.Logger

internal fun Route.registerDatabaseSyncRoutes(
    context: UiServerContext,
    mapper: ObjectMapper,
    logger: Logger,
) {
    get("/api/db/sync/state") {
        call.respond(context.readSyncStateSafely())
    }

    get("/api/db/sync/runs") {
        call.respond(mapOf("runs" to context.listDatabaseSyncRuns(context.parseLimit(call.request.queryParameters["limit"]))))
    }

    get("/api/db/sync/runs/{syncRunId}") {
        call.respond(context.loadDatabaseSyncRunDetailsOrThrow(call.requireRouteParam("syncRunId")))
    }

    post("/api/db/sync/one") {
        val request = call.receive<SyncOneModuleRequest>()
        call.respond(context.syncOneDatabaseModuleFromFiles(request))
    }

    post("/api/db/sync/selected") {
        val payload = call.receiveText()
        val request = context.parseSyncSelectedModulesRequest(mapper, payload, logger)
        call.respond(context.syncSelectedDatabaseModulesFromFiles(request))
    }

    post("/api/db/sync/all") {
        call.respond(context.syncAllDatabaseModulesFromFiles())
    }
}
