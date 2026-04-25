package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsConnectionTestRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsConnectionsTestRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsCredentialsDiagnosticsRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsFilePickRequest
import com.sbrf.lt.platform.ui.model.SqlConsoleSourceSettingsUpdateRequest
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

internal fun Route.registerSqlConsoleSourceSettingsRoutes(context: UiServerContext) {
    get("/api/sql-console/source-settings") {
        call.respond(
            context.sqlConsoleSourceSettingsService.loadSettings(
                context.currentUiConfig(),
            ),
        )
    }

    post("/api/sql-console/source-settings") {
        call.respond(
            context.sqlConsoleSourceSettingsService.saveSettings(
                request = call.receive<SqlConsoleSourceSettingsUpdateRequest>(),
                currentUiConfig = context.currentUiConfig(),
            ),
        )
    }

    post("/api/sql-console/source-settings/test-connection") {
        call.respond(
            context.sqlConsoleSourceSettingsService.testConnection(
                request = call.receive<SqlConsoleSourceSettingsConnectionTestRequest>(),
                currentUiConfig = context.currentUiConfig(),
            ),
        )
    }

    post("/api/sql-console/source-settings/test-connections") {
        call.respond(
            context.sqlConsoleSourceSettingsService.testConnections(
                request = call.receive<SqlConsoleSourceSettingsConnectionsTestRequest>(),
                currentUiConfig = context.currentUiConfig(),
            ),
        )
    }

    post("/api/sql-console/source-settings/credentials/diagnose") {
        call.respond(
            context.sqlConsoleSourceSettingsService.diagnoseCredentials(
                request = call.receive<SqlConsoleSourceSettingsCredentialsDiagnosticsRequest>(),
                currentUiConfig = context.currentUiConfig(),
            ),
        )
    }

    post("/api/sql-console/source-settings/pick-credentials-file") {
        try {
            call.respond(
                context.sqlConsoleSourceSettingsService.pickCredentialsFile(
                    request = call.receive<SqlConsoleSourceSettingsFilePickRequest>(),
                    currentUiConfig = context.currentUiConfig(),
                ),
            )
        } catch (e: IllegalStateException) {
            conflict(e.message.orEmpty())
        }
    }
}
