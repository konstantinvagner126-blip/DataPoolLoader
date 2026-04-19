package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.SqlConsoleSettingsUpdateRequest
import com.sbrf.lt.platform.ui.model.toResponse
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

internal fun Route.registerSqlConsoleStateRoutes(context: UiServerContext) {
    get("/api/sql-console/info") {
        call.respond(context.sqlConsoleService.info().toResponse())
    }

    get("/api/sql-console/state") {
        call.respond(context.sqlConsoleStateService.currentState())
    }

    post("/api/sql-console/state") {
        call.respond(context.sqlConsoleStateService.updateState(call.receive()))
    }

    post("/api/sql-console/settings") {
        val request = call.receive<SqlConsoleSettingsUpdateRequest>()
        context.uiConfigPersistenceService.updateSqlConsoleSettings(
            maxRowsPerShard = request.maxRowsPerShard,
            queryTimeoutSec = request.queryTimeoutSec,
        )
        call.respond(
            context.sqlConsoleService.updateSettings(
                maxRowsPerShard = request.maxRowsPerShard,
                queryTimeoutSec = request.queryTimeoutSec,
            ).toResponse(),
        )
    }
}
