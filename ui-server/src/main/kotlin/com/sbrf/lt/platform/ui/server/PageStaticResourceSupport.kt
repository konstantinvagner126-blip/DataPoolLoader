package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiConfigLoader
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFilePath
import io.ktor.http.formUrlEncode
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

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

private fun loadStaticBytes(
    resourcePath: String,
    classLoader: ClassLoader = UiConfigLoader::class.java.classLoader,
): ByteArray? = classLoader.getResourceAsStream(resourcePath)?.use { it.readBytes() }
