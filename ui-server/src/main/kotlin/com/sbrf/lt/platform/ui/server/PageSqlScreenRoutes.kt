package com.sbrf.lt.platform.ui.server

import io.ktor.server.routing.Route

internal fun Route.registerPageSqlScreenRoutes() {
    registerComposeRedirect("/sql-console", "screen" to "sql-console")
    registerComposeRedirect(
        "/sql-console-objects",
        "screen" to "sql-console-objects",
        forwardedParams = listOf("query", "source", "schema", "object", "type"),
    )
    registerComposeRedirect("/run-history-cleanup", "screen" to "run-history-cleanup")
}
