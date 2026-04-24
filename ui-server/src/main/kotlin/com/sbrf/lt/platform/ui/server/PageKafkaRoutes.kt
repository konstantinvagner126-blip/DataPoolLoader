package com.sbrf.lt.platform.ui.server

import io.ktor.server.routing.Route

internal fun Route.registerPageKafkaRoutes() {
    registerComposeRedirect(
        "/kafka",
        "screen" to "kafka",
        forwardedParams = listOf("clusterId", "topic"),
    )
}
