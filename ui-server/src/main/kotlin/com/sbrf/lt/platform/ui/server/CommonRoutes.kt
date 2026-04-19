package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.routing.Route

/**
 * Общие UI API: runtime mode, файловые модули, credentials, cleanup и websocket.
 */
internal fun Route.registerCommonRoutes(
    context: UiServerContext,
    mapper: ObjectMapper,
) {
    registerCommonRuntimeRoutes(context)
    registerCommonFilesModuleRoutes(context, mapper)
    registerCommonFilesRunRoutes(context, mapper)
    registerCommonMaintenanceRoutes(context, mapper)
}
