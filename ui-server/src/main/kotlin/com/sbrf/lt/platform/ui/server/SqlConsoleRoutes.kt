package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.SqlConsoleObjectSearchRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleExportRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleQueryRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSettingsUpdateRequest
import com.sbrf.lt.platform.ui.model.toResponse
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/**
 * API SQL-консоли: состояние, проверки, выполнение, экспорт и async polling.
 */
internal fun Route.registerSqlConsoleRoutes(context: UiServerContext) {
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

    post("/api/sql-console/connections/check") {
        call.respond(context.checkSqlConsoleConnections())
    }

    post("/api/sql-console/objects/search") {
        val request = call.receive<SqlConsoleObjectSearchRequest>()
        call.respond(context.searchSqlConsoleObjects(request))
    }

    post("/api/sql-console/query") {
        val request = call.receive<SqlConsoleQueryRequest>()
        call.respond(context.executeSqlConsoleQuery(request))
    }

    post("/api/sql-console/export/source-csv") {
        val request = call.receive<SqlConsoleExportRequest>()
        val shardName = request.requireShardName()
        val bytes = context.sqlConsoleExportService.exportShardCsv(request.result, shardName)
        call.response.headers.append(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName,
                "$shardName.csv",
            ).toString(),
        )
        call.respondBytes(bytes, ContentType.parse("text/csv; charset=utf-8"))
    }

    post("/api/sql-console/export/all-zip") {
        val request = call.receive<SqlConsoleExportRequest>()
        val bytes = context.sqlConsoleExportService.exportAllZip(request.result)
        call.response.headers.append(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName,
                "sql-console-results.zip",
            ).toString(),
        )
        call.respondBytes(bytes, ContentType.Application.Zip)
    }

    post("/api/sql-console/query/start") {
        val request = call.receive<SqlConsoleQueryRequest>()
        call.respond(context.startSqlConsoleQuery(request))
    }

    get("/api/sql-console/query/{id}") {
        call.respond(context.loadSqlConsoleExecution(call.requireSqlConsoleExecutionId()))
    }

    post("/api/sql-console/query/{id}/cancel") {
        call.respond(context.cancelSqlConsoleExecution(call.requireSqlConsoleExecutionId()))
    }

    post("/api/sql-console/query/{id}/commit") {
        call.respond(context.commitSqlConsoleExecution(call.requireSqlConsoleExecutionId()))
    }

    post("/api/sql-console/query/{id}/rollback") {
        call.respond(context.rollbackSqlConsoleExecution(call.requireSqlConsoleExecutionId()))
    }
}
