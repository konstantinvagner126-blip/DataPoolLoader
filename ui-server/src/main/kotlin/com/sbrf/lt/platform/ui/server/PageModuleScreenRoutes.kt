package com.sbrf.lt.platform.ui.server

import io.ktor.server.routing.Route

internal fun Route.registerPageModuleScreenRoutes(context: UiServerContext) {
    registerPageModuleEditorRoutes(context)
    registerPageModuleRunsRoutes(context)
}
