package com.sbrf.lt.platform.ui.server

import io.ktor.server.routing.Route

internal fun Route.registerPageStaticRoutes() {
    registerStaticTextPage("/help", "static/help.html")
    registerStaticTextPage("/help/api", "static/help-api.html")
    registerStaticResourceRoute()
}
