package com.sbrf.lt.platform.ui.server

import io.ktor.server.routing.Route

internal fun Route.registerPageSqlScreenRoutes() {
    registerComposeRedirect(
        "/sql-console",
        "screen" to "sql-console",
        forwardedParams = listOf("workspaceId"),
    )
    registerComposeRedirect(
        "/sql-console-objects",
        "screen" to "sql-console-objects",
        forwardedParams = listOf("workspaceId", "query", "source", "schema", "object", "type"),
    )
    registerComposeRedirect(
        "/sql-console-history",
        "screen" to "sql-console-history",
        forwardedParams = listOf("workspaceId"),
    )
    registerComposeRedirect("/run-history-cleanup", "screen" to "run-history-cleanup")
}
