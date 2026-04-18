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
