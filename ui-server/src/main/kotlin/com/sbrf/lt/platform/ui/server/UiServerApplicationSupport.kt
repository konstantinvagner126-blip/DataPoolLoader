package com.sbrf.lt.platform.ui.server

import io.ktor.server.application.Application
import io.ktor.server.routing.routing

internal fun Application.installUiServerApplication(serverContext: UiServerContext) {
    val logger = defaultUiServerLogger()
    val mapper = createUiServerObjectMapper()
    installUiServerPlugins(logger)
    routing {
        registerUiRoutes(serverContext, mapper)
    }
}
