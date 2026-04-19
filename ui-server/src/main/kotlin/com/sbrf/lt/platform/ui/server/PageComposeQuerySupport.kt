package com.sbrf.lt.platform.ui.server

import io.ktor.http.Parameters
import io.ktor.http.formUrlEncode
import io.ktor.server.application.ApplicationCall

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

internal fun Parameters.toQueryParametersMap(): Map<String, List<String>> =
    names().associateWith { name -> getAll(name).orEmpty() }
