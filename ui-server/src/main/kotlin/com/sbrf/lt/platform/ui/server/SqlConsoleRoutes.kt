package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.SqlConsoleExportRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleQueryRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSettingsUpdateRequest
import com.sbrf.lt.platform.ui.model.toResponse
import com.sbrf.lt.platform.ui.model.toStartResponse
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
        val tempDir = kotlin.io.path.createTempDirectory("datapool-ui-sql-console-check-")
        try {
            val credentialsPath = context.filesRunService.materializeCredentialsFile(tempDir)
            call.respond(
                context.sqlConsoleService.checkConnections(credentialsPath = credentialsPath).toResponse(
                    configured = context.sqlConsoleService.info().configured,
                ),
            )
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    post("/api/sql-console/query") {
        val request = call.receive<SqlConsoleQueryRequest>()
        val tempDir = kotlin.io.path.createTempDirectory("datapool-ui-sql-console-")
        try {
            val credentialsPath = context.filesRunService.materializeCredentialsFile(tempDir)
            call.respond(
                context.sqlConsoleService.executeQuery(
                    rawSql = request.sql,
                    credentialsPath = credentialsPath,
                    selectedSourceNames = request.selectedSourceNames,
                ).toResponse(),
            )
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    post("/api/sql-console/export/source-csv") {
        val request = call.receive<SqlConsoleExportRequest>()
        val shardName = requireNotNull(request.shardName?.takeIf { it.isNotBlank() }) {
            "Для CSV-экспорта нужно указать shardName."
        }
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
        val tempDir = kotlin.io.path.createTempDirectory("datapool-ui-sql-console-")
        val credentialsPath = try {
            context.filesRunService.materializeCredentialsFile(tempDir)
        } catch (ex: Exception) {
            tempDir.toFile().deleteRecursively()
            throw ex
        }
        try {
            call.respond(
                context.sqlConsoleQueryManager.startQuery(
                    sql = request.sql,
                    credentialsPath = credentialsPath,
                    selectedSourceNames = request.selectedSourceNames,
                    cleanupDir = tempDir,
                ).toStartResponse(),
            )
        } catch (ex: Exception) {
            tempDir.toFile().deleteRecursively()
            throw ex
        }
    }

    get("/api/sql-console/query/{id}") {
        call.respond(context.sqlConsoleQueryManager.snapshot(requireNotNull(call.parameters["id"])).toResponse())
    }

    post("/api/sql-console/query/{id}/cancel") {
        call.respond(context.sqlConsoleQueryManager.cancel(requireNotNull(call.parameters["id"])).toResponse())
    }
}
