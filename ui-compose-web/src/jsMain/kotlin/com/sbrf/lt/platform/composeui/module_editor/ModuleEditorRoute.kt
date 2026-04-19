package com.sbrf.lt.platform.composeui.module_editor

fun parseModuleEditorRoute(params: Map<String, String>): ModuleEditorRouteState? {
    val storage = params["storage"]?.trim()?.lowercase()
    if (storage != "files" && storage != "database") {
        return null
    }
    return ModuleEditorRouteState(
        storage = storage,
        moduleId = params["module"]?.trim()?.ifBlank { null },
        includeHidden = params["includeHidden"] == "true" || params["includeHidden"] == "1",
        openCreateDialog = params["openCreate"] == "true" || params["openCreate"] == "1",
    )
}

fun buildRunsHref(
    route: ModuleEditorRouteState,
    moduleId: String?,
): String {
    val includeHiddenPart = if (route.includeHidden) "&includeHidden=true" else ""
    return "/module-runs?storage=${route.storage}&module=${moduleId.orEmpty()}$includeHiddenPart"
}

fun buildComposeEditorUrl(
    storage: String,
    moduleId: String?,
    includeHidden: Boolean,
): String {
    val query = buildString {
        var separator = '?'
        if (!moduleId.isNullOrBlank()) {
            append(separator)
            append("module=")
            append(moduleId)
            separator = '&'
        }
        if (storage == "database" && includeHidden) {
            append(separator)
            append("includeHidden=true")
        }
    }
    return buildPrimaryEditorUrl(storage, query)
}

fun buildPrimaryEditorUrl(
    storage: String,
    query: String,
): String =
    if (storage == "database") {
        "/db-modules$query"
    } else {
        "/modules$query"
    }
