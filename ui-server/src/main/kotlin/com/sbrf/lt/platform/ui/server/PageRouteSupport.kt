package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import io.ktor.http.formUrlEncode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondRedirect

internal suspend fun ApplicationCall.redirectToComposeBundle(
    queryParameters: Map<String, List<String>> = emptyMap(),
) {
    respondRedirect(composeBundleLocation(queryParameters), permanent = false)
}

internal suspend fun UiServerContext.redirectToModeAccessError(
    call: ApplicationCall,
    modeAccessError: String,
) {
    call.redirectToComposeBundle(mapOf("modeAccessError" to listOf(modeAccessError)))
}

internal suspend fun UiServerContext.requirePageModeOrRedirect(
    call: ApplicationCall,
    expectedMode: UiModuleStoreMode,
    modeAccessError: String,
): Boolean {
    if (currentRuntimeContext().effectiveMode == expectedMode) {
        return true
    }
    redirectToModeAccessError(call, modeAccessError)
    return false
}

internal fun ApplicationCall.composeQueryParameters(
    vararg fixedParams: Pair<String, String>,
    forwardedParams: List<String> = emptyList(),
): LinkedHashMap<String, List<String>> =
    linkedMapOf<String, List<String>>().also { params ->
        fixedParams.forEach { (key, value) ->
            params[key] = listOf(value)
        }
        forwardedParams.forEach { name ->
            request.queryParameters[name]?.let { params[name] = listOf(it) }
        }
    }

internal fun composeBundleLocation(
    queryParameters: Map<String, List<String>> = emptyMap(),
): String {
    val query = queryParameters
        .flatMap { (key, values) -> values.map { key to it } }
        .formUrlEncode()
    return if (query.isBlank()) {
        "/static/compose-app/index.html"
    } else {
        "/static/compose-app/index.html?$query"
    }
}
