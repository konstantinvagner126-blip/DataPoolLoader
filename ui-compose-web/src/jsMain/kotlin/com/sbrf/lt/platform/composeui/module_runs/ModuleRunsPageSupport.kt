package com.sbrf.lt.platform.composeui.module_runs

import kotlinx.serialization.json.Json

internal val technicalDiagnosticsJson = Json {
    prettyPrint = true
}

internal fun buildBackHref(route: ModuleRunsRouteState): String =
    if (route.storage == "database") {
        "/db-modules?module=${route.moduleId}"
    } else {
        "/modules?module=${route.moduleId}"
    }
