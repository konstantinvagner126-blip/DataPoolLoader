package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.routing.Route

internal fun Route.registerUiRoutes(context: UiServerContext, mapper: ObjectMapper) {
    registerPageRoutes(context)
    registerModuleRunRoutes(context)
    registerDatabaseRoutes(context, mapper)
    registerCommonRoutes(context, mapper)
    registerKafkaRoutes(context)
    registerSqlConsoleRoutes(context)
}
