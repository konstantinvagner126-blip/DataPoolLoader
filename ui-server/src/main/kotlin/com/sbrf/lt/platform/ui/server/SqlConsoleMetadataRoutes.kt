package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.SqlConsoleObjectInspectorRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleObjectSearchRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

internal fun Route.registerSqlConsoleMetadataRoutes(context: UiServerContext) {
    post("/api/sql-console/objects/search") {
        val request = call.receive<SqlConsoleObjectSearchRequest>()
        call.respond(context.searchSqlConsoleObjects(request))
    }
    post("/api/sql-console/objects/inspect") {
        val request = call.receive<SqlConsoleObjectInspectorRequest>()
        call.respond(context.inspectSqlConsoleObject(request))
    }
}
