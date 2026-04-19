package com.sbrf.lt.platform.ui.server

import io.ktor.server.application.ApplicationCall

internal fun ApplicationCall.requireRouteParam(name: String): String =
    parameters[name]?.takeIf { it.isNotBlank() }
        ?: badRequest("В route отсутствует обязательный параметр '$name'.")
