package com.sbrf.lt.platform.ui.server

import io.ktor.server.routing.Route

internal fun Route.registerPageInfoRoutes() {
    registerComposeRedirect("/about", "screen" to "about")
}
