package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Статические страницы UI и guards доступа по текущему режиму.
 */
internal fun Route.registerPageRoutes(context: UiServerContext) {
    get("/") { call.redirectToComposeBundle(call.request.queryParameters.toQueryParametersMap()) }
    registerModeGuardedComposeRedirect(
        context = context,
        path = "/modules",
        expectedMode = UiModuleStoreMode.FILES,
        modeAccessError = "modules",
        "screen" to "module-editor",
        "storage" to "files",
        forwardedParams = listOf("module"),
    )
    registerStaticTextPage("/help", "static/help.html")
    registerStaticTextPage("/help/api", "static/help-api.html")
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
    registerComposeRedirect("/compose-sql-console", "screen" to "sql-console")
    registerComposeRedirect(
        "/compose-sql-console-objects",
        "screen" to "sql-console-objects",
        forwardedParams = listOf("query", "source", "schema", "object", "type"),
    )
    get("/module-runs") {
        when (call.request.queryParameters["storage"]?.lowercase()) {
            "files" -> {
                if (!context.requirePageModeOrRedirect(call, UiModuleStoreMode.FILES, "modules")) {
                    return@get
                }
            }
            "database" -> {
                if (!context.requirePageModeOrRedirect(call, UiModuleStoreMode.DATABASE, "db-modules")) {
                    return@get
                }
            }
            else -> {
                call.redirectToComposeBundle()
                return@get
            }
        }
        call.redirectToComposeBundle(
            call.composeQueryParameters(
                "screen" to "module-runs",
                forwardedParams = listOf("storage", "module"),
            ),
        )
    }
    registerComposeRedirect("/sql-console", "screen" to "sql-console")
    registerComposeRedirect(
        "/sql-console-objects",
        "screen" to "sql-console-objects",
        forwardedParams = listOf("query", "source", "schema", "object", "type"),
    )
    registerModeGuardedComposeRedirect(
        context = context,
        path = "/db-modules",
        expectedMode = UiModuleStoreMode.DATABASE,
        modeAccessError = "db-modules",
        "screen" to "module-editor",
        "storage" to "database",
        forwardedParams = listOf("module", "includeHidden"),
    )
    registerModeGuardedComposeRedirect(
        context = context,
        path = "/db-modules/new",
        expectedMode = UiModuleStoreMode.DATABASE,
        modeAccessError = "db-modules",
        "screen" to "module-editor",
        "storage" to "database",
        "openCreate" to "true",
    )
    registerModeGuardedComposeRedirect(
        context = context,
        path = "/db-sync",
        expectedMode = UiModuleStoreMode.DATABASE,
        modeAccessError = "db-sync",
        "screen" to "module-sync",
    )
    registerComposeRedirect("/run-history-cleanup", "screen" to "run-history-cleanup")
    registerStaticResourceRoute()
}
