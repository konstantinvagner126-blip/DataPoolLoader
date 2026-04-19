package com.sbrf.lt.platform.ui.server

import io.ktor.server.routing.Route

internal fun Route.registerDatabaseModuleLifecycleRoutes(context: UiServerContext) {
    registerDatabaseModuleCatalogRoutes(context)
    registerDatabaseModuleMutationRoutes(context)
}
