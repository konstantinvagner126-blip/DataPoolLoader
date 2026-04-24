package com.sbrf.lt.platform.ui.server

import io.ktor.server.routing.Route

/**
 * Статические страницы UI и guards доступа по текущему режиму.
 */
internal fun Route.registerPageRoutes(context: UiServerContext) {
    registerPageScreenRoutes(context)
    registerPageStaticRoutes()
}
