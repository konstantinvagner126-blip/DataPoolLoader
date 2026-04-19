package com.sbrf.lt.platform.ui.server

import io.ktor.server.routing.Route

internal fun Route.registerDatabaseModuleMutationRoutes(context: UiServerContext) {
    registerDatabaseModuleWorkingCopyRoutes(context)
    registerDatabaseModuleRegistryMutationRoutes(context)
}
