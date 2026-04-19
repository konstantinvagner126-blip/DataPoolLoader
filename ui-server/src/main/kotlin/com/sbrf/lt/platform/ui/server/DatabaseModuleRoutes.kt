package com.sbrf.lt.platform.ui.server

import io.ktor.server.routing.Route

internal fun Route.registerDatabaseModuleRoutes(context: UiServerContext) {
    registerDatabaseModuleLifecycleRoutes(context)
    registerDatabaseModuleRunRoutes(context)
}
