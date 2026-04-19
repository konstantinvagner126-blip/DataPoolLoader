package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFilePath
import io.ktor.http.formUrlEncode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

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

internal fun Route.registerComposeRedirect(
    path: String,
    vararg fixedParams: Pair<String, String>,
    forwardedParams: List<String> = emptyList(),
) {
    get(path) {
        call.redirectToComposeBundle(
            call.composeQueryParameters(*fixedParams, forwardedParams = forwardedParams),
        )
    }
}

internal fun Route.registerModeGuardedComposeRedirect(
    context: UiServerContext,
    path: String,
    expectedMode: UiModuleStoreMode,
    modeAccessError: String,
    vararg fixedParams: Pair<String, String>,
    forwardedParams: List<String> = emptyList(),
) {
    get(path) {
        if (!context.requirePageModeOrRedirect(call, expectedMode, modeAccessError)) {
            return@get
        }
        call.redirectToComposeBundle(
            call.composeQueryParameters(*fixedParams, forwardedParams = forwardedParams),
        )
    }
}

internal fun Route.registerStaticTextPage(
    path: String,
    resourcePath: String,
) {
    get(path) {
        call.respondText(loadStaticText(resourcePath), ContentType.Text.Html)
    }
}

internal fun Route.registerStaticResourceRoute() {
    get("/static/{resourcePath...}") {
        val path = call.parameters.getAll("resourcePath").orEmpty().joinToString("/")
        if (path.isBlank() || path.contains("..")) {
            call.respondText("", status = HttpStatusCode.NotFound)
            return@get
        }
        if (path == "compose-spike" || path.startsWith("compose-spike/")) {
            val migratedPath = path.removePrefix("compose-spike").removePrefix("/")
            val query = call.request.queryParameters.toQueryParametersMap()
                .flatMap { (key, values) -> values.map { key to it } }
                .formUrlEncode()
            val target = buildString {
                append("/static/compose-app")
                if (migratedPath.isNotBlank()) {
                    append("/")
                    append(migratedPath)
                }
                if (query.isNotBlank()) {
                    append("?")
                    append(query)
                }
            }
            call.respondRedirect(target, permanent = false)
            return@get
        }
        val resourcePath = "static/$path"
        val content = loadStaticBytes(resourcePath)
        if (content == null) {
            call.respondText("", status = HttpStatusCode.NotFound)
            return@get
        }
        call.respondBytes(bytes = content, contentType = ContentType.defaultForFilePath(resourcePath))
    }
}

internal fun io.ktor.http.Parameters.toQueryParametersMap(): Map<String, List<String>> =
    names().associateWith { name -> getAll(name).orEmpty() }

private fun loadStaticBytes(
    resourcePath: String,
    classLoader: ClassLoader = UiConfigLoader::class.java.classLoader,
): ByteArray? = classLoader.getResourceAsStream(resourcePath)?.use { it.readBytes() }
