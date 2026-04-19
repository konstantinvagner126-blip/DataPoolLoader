package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.SqlConsoleExportRequest
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

internal fun Route.registerSqlConsoleExportRoutes(context: UiServerContext) {
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
}
