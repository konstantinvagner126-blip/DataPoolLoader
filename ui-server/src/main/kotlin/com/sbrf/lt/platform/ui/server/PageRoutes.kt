package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFilePath
import io.ktor.http.formUrlEncode
import io.ktor.server.application.call
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Статические страницы UI и guards доступа по текущему режиму.
 */
internal fun Route.registerPageRoutes(context: UiServerContext) {
    get("/") {
        call.redirectToComposeBundle(call.request.queryParameters.toQueryParametersMap())
    }
    get("/modules") {
        if (!context.requirePageModeOrRedirect(call, UiModuleStoreMode.FILES, "modules")) {
            return@get
        }
        call.redirectToComposeBundle(
            call.composeQueryParameters(
                "screen" to "module-editor",
                "storage" to "files",
                forwardedParams = listOf("module"),
            ),
        )
    }
    get("/help") {
        call.respondText(loadStaticText("static/help.html"), io.ktor.http.ContentType.Text.Html)
    }
    get("/help/api") {
        call.respondText(loadStaticText("static/help-api.html"), io.ktor.http.ContentType.Text.Html)
    }
    get("/compose-runs") {
        call.redirectToComposeBundle(
            call.composeQueryParameters(
                "screen" to "module-runs",
                forwardedParams = listOf("storage", "module"),
            ),
        )
    }
    get("/compose-editor") {
        call.redirectToComposeBundle(
            call.composeQueryParameters(
                "screen" to "module-editor",
                forwardedParams = listOf("storage", "module", "includeHidden", "openCreate"),
            ),
        )
    }
    get("/compose-sync") {
        call.redirectToComposeBundle(call.composeQueryParameters("screen" to "module-sync"))
    }
    get("/compose-run-history-cleanup") {
        call.redirectToComposeBundle(call.composeQueryParameters("screen" to "run-history-cleanup"))
    }
    get("/compose-sql-console") {
        call.redirectToComposeBundle(call.composeQueryParameters("screen" to "sql-console"))
    }
    get("/compose-sql-console-objects") {
        call.redirectToComposeBundle(
            call.composeQueryParameters(
                "screen" to "sql-console-objects",
                forwardedParams = listOf("query", "source", "schema", "object", "type"),
            ),
        )
    }
    get("/module-runs") {
        when (call.request.queryParameters["storage"]?.lowercase()) {
            "files" -> {
                if (!context.requirePageModeOrRedirect(call, UiModuleStoreMode.FILES, "modules")) {
                    return@get
                }
            }
            "database" -> {
                if (!context.requirePageModeOrRedirect(call, UiModuleStoreMode.DATABASE, "db-modules")) {
                    return@get
                }
            }
            else -> {
                call.redirectToComposeBundle()
                return@get
            }
        }
        call.redirectToComposeBundle(
            call.composeQueryParameters(
                "screen" to "module-runs",
                forwardedParams = listOf("storage", "module"),
            ),
        )
    }
    get("/sql-console") {
        call.redirectToComposeBundle(call.composeQueryParameters("screen" to "sql-console"))
    }
    get("/sql-console-objects") {
        call.redirectToComposeBundle(
            call.composeQueryParameters(
                "screen" to "sql-console-objects",
                forwardedParams = listOf("query", "source", "schema", "object", "type"),
            ),
        )
    }
    get("/db-modules") {
        if (!context.requirePageModeOrRedirect(call, UiModuleStoreMode.DATABASE, "db-modules")) {
            return@get
        }
        call.redirectToComposeBundle(
            call.composeQueryParameters(
                "screen" to "module-editor",
                "storage" to "database",
                forwardedParams = listOf("module", "includeHidden"),
            ),
        )
    }
    get("/db-modules/new") {
        if (!context.requirePageModeOrRedirect(call, UiModuleStoreMode.DATABASE, "db-modules")) {
            return@get
        }
        call.redirectToComposeBundle(
            call.composeQueryParameters(
                "screen" to "module-editor",
                "storage" to "database",
                "openCreate" to "true",
            ),
        )
    }
    get("/db-sync") {
        if (!context.requirePageModeOrRedirect(call, UiModuleStoreMode.DATABASE, "db-sync")) {
            return@get
        }
        call.redirectToComposeBundle(call.composeQueryParameters("screen" to "module-sync"))
    }
    get("/run-history-cleanup") {
        call.redirectToComposeBundle(call.composeQueryParameters("screen" to "run-history-cleanup"))
    }
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

private fun io.ktor.http.Parameters.toQueryParametersMap(): Map<String, List<String>> =
    names().associateWith { name -> getAll(name).orEmpty() }

private fun loadStaticBytes(
    resourcePath: String,
    classLoader: ClassLoader = UiConfigLoader::class.java.classLoader,
): ByteArray? = classLoader.getResourceAsStream(resourcePath)?.use { it.readBytes() }
