package com.sbrf.lt.platform.composeui.module_editor

internal fun formatEditorTimeoutSeconds(value: Int?): String =
    when (value) {
        null -> "Не задан"
        else -> "${value}с"
    }

internal fun buildRunsHref(
    route: ModuleEditorRouteState,
    moduleId: String?,
): String {
    val includeHiddenPart = if (route.includeHidden) "&includeHidden=true" else ""
    return "/module-runs?storage=${route.storage}&module=${moduleId.orEmpty()}$includeHiddenPart"
}

internal fun buildComposeEditorUrl(
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

internal fun buildPrimaryEditorUrl(
    storage: String,
    query: String,
): String =
    if (storage == "database") {
        "/db-modules$query"
    } else {
        "/modules$query"
    }

internal fun validationBadgeClass(validationStatus: String): String =
    when (validationStatus.uppercase()) {
        "VALID" -> "module-validation-badge-valid"
        "WARNING" -> "module-validation-badge-warning"
        else -> "module-validation-badge-invalid"
    }
