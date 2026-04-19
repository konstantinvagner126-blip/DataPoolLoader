package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.SqlConsoleQueryRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

internal fun Route.registerSqlConsoleQueryRoutes(context: UiServerContext) {
    post("/api/sql-console/connections/check") {
        call.respond(context.checkSqlConsoleConnections())
    }

    post("/api/sql-console/query") {
        val request = call.receive<SqlConsoleQueryRequest>()
        call.respond(context.executeSqlConsoleQuery(request))
    }
}
