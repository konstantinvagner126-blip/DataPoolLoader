package com.sbrf.lt.platform.ui.server

import io.ktor.server.routing.Route

internal fun Route.registerPageComposeAliasRoutes() {
    registerComposeRedirect(
        "/compose-runs",
        "screen" to "module-runs",
        forwardedParams = listOf("storage", "module"),
    )
    registerComposeRedirect(
        "/compose-editor",
        "screen" to "module-editor",
        forwardedParams = listOf("storage", "module", "includeHidden", "openCreate"),
    )
    registerComposeRedirect("/compose-sync", "screen" to "module-sync")
    registerComposeRedirect("/compose-run-history-cleanup", "screen" to "run-history-cleanup")
    registerComposeRedirect(
        "/compose-sql-console",
        "screen" to "sql-console",
        forwardedParams = listOf("workspaceId"),
    )
    registerComposeRedirect(
        "/compose-sql-console-objects",
        "screen" to "sql-console-objects",
        forwardedParams = listOf("workspaceId", "query", "source", "schema", "object", "type"),
    )
}
