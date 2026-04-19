package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.registerPageModuleRunsRoutes(context: UiServerContext) {
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
}
