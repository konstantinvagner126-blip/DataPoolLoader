package com.sbrf.lt.platform.ui.server

import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.registerPageScreenRoutes(context: UiServerContext) {
    get("/") { call.redirectToComposeBundle(call.request.queryParameters.toQueryParametersMap()) }
    registerPageInfoRoutes()
    registerPageModuleScreenRoutes(context)
    registerPageSqlScreenRoutes()
}
