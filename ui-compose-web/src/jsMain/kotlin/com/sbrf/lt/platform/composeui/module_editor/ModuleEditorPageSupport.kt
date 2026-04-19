package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import kotlinx.browser.window
import org.w3c.files.File
import org.w3c.xhr.FormData

internal fun formatEditorTimeoutSeconds(value: Int?): String =
    when (value) {
        null -> "Не задан"
        else -> "${value}с"
    }

internal fun loadEditorSectionExpanded(
    sectionStateKey: String?,
    defaultExpanded: Boolean,
): Boolean {
    if (sectionStateKey == null) {
        return defaultExpanded
    }
    return runCatching { window.localStorage.getItem(sectionStateKey) }
        .getOrNull()
        ?.let { storedValue -> storedValue == "true" }
        ?: defaultExpanded
}

internal fun saveEditorSectionExpanded(
    sectionStateKey: String?,
    expanded: Boolean,
) {
    if (sectionStateKey == null) {
        return
    }
    runCatching { window.localStorage.setItem(sectionStateKey, expanded.toString()) }
}

internal fun buildRunsHref(
    route: ModuleEditorRouteState,
    moduleId: String?,
): String {
    val includeHiddenPart = if (route.includeHidden) "&includeHidden=true" else ""
    return "/module-runs?storage=${route.storage}&module=${moduleId.orEmpty()}$includeHiddenPart"
}

internal fun buildEditorWebSocketUrl(): String {
    val protocol = if (window.location.protocol == "https:") "wss" else "ws"
    return "$protocol://${window.location.host}/ws"
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

internal suspend fun uploadCredentialsFile(
    httpClient: ComposeHttpClient,
    file: File,
): CredentialsStatusResponse {
    val formData = FormData()
    formData.append("file", file, file.name)
    return httpClient.postFormData(
        path = "/api/credentials/upload",
        formData = formData,
        deserializer = CredentialsStatusResponse.serializer(),
    )
}

internal fun validationBadgeClass(validationStatus: String): String =
    when (validationStatus.uppercase()) {
        "VALID" -> "module-validation-badge-valid"
        "WARNING" -> "module-validation-badge-warning"
        else -> "module-validation-badge-invalid"
    }
