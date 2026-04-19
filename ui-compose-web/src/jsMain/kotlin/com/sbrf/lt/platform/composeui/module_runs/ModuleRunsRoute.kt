package com.sbrf.lt.platform.composeui.module_runs

fun parseModuleRunsRoute(params: Map<String, String>): ModuleRunsRouteState? {
    val storage = params["storage"]?.lowercase() ?: return null
    val moduleId = params["module"] ?: params["id"] ?: return null
    if (storage != "files" && storage != "database") {
        return null
    }
    if (moduleId.isBlank()) {
        return null
    }
    return ModuleRunsRouteState(
        storage = storage,
        moduleId = moduleId,
    )
}

fun ModuleRunsRouteState.backHref(): String =
    if (storage == "database") {
        "/db-modules?module=$moduleId"
    } else {
        "/modules?module=$moduleId"
    }
