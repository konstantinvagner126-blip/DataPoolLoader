package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.SqlConsoleQueryRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleExecutionOwnerActionRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

internal fun Route.registerSqlConsoleAsyncQueryRoutes(context: UiServerContext) {
    post("/api/sql-console/query/start") {
        val request = call.receive<SqlConsoleQueryRequest>()
        call.respond(context.startSqlConsoleQuery(request))
    }

    get("/api/sql-console/query/{id}") {
        call.respond(context.loadSqlConsoleExecution(call.requireSqlConsoleExecutionId()))
    }

    post("/api/sql-console/query/{id}/heartbeat") {
        call.respond(
            context.heartbeatSqlConsoleExecution(
                executionId = call.requireSqlConsoleExecutionId(),
                request = call.receive<SqlConsoleExecutionOwnerActionRequest>(),
            ),
        )
    }

    post("/api/sql-console/query/{id}/release") {
        call.respond(
            context.releaseSqlConsoleExecutionOwnership(
                executionId = call.requireSqlConsoleExecutionId(),
                request = call.receive<SqlConsoleExecutionOwnerActionRequest>(),
            ),
        )
    }

    post("/api/sql-console/query/{id}/cancel") {
        call.respond(
            context.cancelSqlConsoleExecution(
                executionId = call.requireSqlConsoleExecutionId(),
                request = call.receive<SqlConsoleExecutionOwnerActionRequest>(),
            ),
        )
    }

    post("/api/sql-console/query/{id}/commit") {
        call.respond(
            context.commitSqlConsoleExecution(
                executionId = call.requireSqlConsoleExecutionId(),
                request = call.receive<SqlConsoleExecutionOwnerActionRequest>(),
            ),
        )
    }

    post("/api/sql-console/query/{id}/rollback") {
        call.respond(
            context.rollbackSqlConsoleExecution(
                executionId = call.requireSqlConsoleExecutionId(),
                request = call.receive<SqlConsoleExecutionOwnerActionRequest>(),
            ),
        )
    }
}
