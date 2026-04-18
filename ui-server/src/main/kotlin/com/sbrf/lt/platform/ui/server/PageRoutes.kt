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
        call.respondRedirect(composeBundleLocation(call.request.queryParameters.toQueryParametersMap()), permanent = false)
    }
    get("/modules") {
        if (context.currentRuntimeContext().effectiveMode != UiModuleStoreMode.FILES) {
            call.respondRedirect(composeBundleLocation(mapOf("modeAccessError" to listOf("modules"))), permanent = false)
            return@get
        }
        val params = linkedMapOf<String, List<String>>(
            "screen" to listOf("module-editor"),
            "storage" to listOf("files"),
        )
        call.request.queryParameters["module"]?.let { params["module"] = listOf(it) }
        call.respondRedirect(composeBundleLocation(params), permanent = false)
    }
    get("/help") {
        call.respondText(loadStaticText("static/help.html"), io.ktor.http.ContentType.Text.Html)
    }
    get("/help/api") {
        call.respondText(loadStaticText("static/help-api.html"), io.ktor.http.ContentType.Text.Html)
    }
    get("/compose-runs") {
        val params = linkedMapOf<String, List<String>>("screen" to listOf("module-runs"))
        call.request.queryParameters["storage"]?.let { params["storage"] = listOf(it) }
        call.request.queryParameters["module"]?.let { params["module"] = listOf(it) }
        call.respondRedirect(composeBundleLocation(params), permanent = false)
    }
    get("/compose-editor") {
        val params = linkedMapOf<String, List<String>>("screen" to listOf("module-editor"))
        call.request.queryParameters["storage"]?.let { params["storage"] = listOf(it) }
        call.request.queryParameters["module"]?.let { params["module"] = listOf(it) }
        call.request.queryParameters["includeHidden"]?.let { params["includeHidden"] = listOf(it) }
        call.request.queryParameters["openCreate"]?.let { params["openCreate"] = listOf(it) }
        call.respondRedirect(composeBundleLocation(params), permanent = false)
    }
    get("/compose-sync") {
        call.respondRedirect(
            composeBundleLocation(linkedMapOf("screen" to listOf("module-sync"))),
            permanent = false,
        )
    }
    get("/compose-sql-console") {
        call.respondRedirect(
            composeBundleLocation(linkedMapOf("screen" to listOf("sql-console"))),
            permanent = false,
        )
    }
    get("/module-runs") {
        when (call.request.queryParameters["storage"]?.lowercase()) {
            "files" -> {
                if (context.currentRuntimeContext().effectiveMode != UiModuleStoreMode.FILES) {
                    call.respondRedirect(composeBundleLocation(mapOf("modeAccessError" to listOf("modules"))), permanent = false)
                    return@get
                }
            }
            "database" -> {
                if (context.currentRuntimeContext().effectiveMode != UiModuleStoreMode.DATABASE) {
                    call.respondRedirect(composeBundleLocation(mapOf("modeAccessError" to listOf("db-modules"))), permanent = false)
                    return@get
                }
            }
            else -> {
                call.respondRedirect(composeBundleLocation(), permanent = false)
                return@get
            }
        }
        val params = linkedMapOf<String, List<String>>("screen" to listOf("module-runs"))
        call.request.queryParameters["storage"]?.let { params["storage"] = listOf(it) }
        call.request.queryParameters["module"]?.let { params["module"] = listOf(it) }
        call.respondRedirect(composeBundleLocation(params), permanent = false)
    }
    get("/sql-console") {
        call.respondRedirect(
            composeBundleLocation(linkedMapOf("screen" to listOf("sql-console"))),
            permanent = false,
        )
    }
    get("/db-modules") {
        if (context.currentRuntimeContext().effectiveMode != UiModuleStoreMode.DATABASE) {
            call.respondRedirect(composeBundleLocation(mapOf("modeAccessError" to listOf("db-modules"))), permanent = false)
            return@get
        }
        val params = linkedMapOf<String, List<String>>(
            "screen" to listOf("module-editor"),
            "storage" to listOf("database"),
        )
        call.request.queryParameters["module"]?.let { params["module"] = listOf(it) }
        call.request.queryParameters["includeHidden"]?.let { params["includeHidden"] = listOf(it) }
        call.respondRedirect(composeBundleLocation(params), permanent = false)
    }
    get("/db-modules/new") {
        if (context.currentRuntimeContext().effectiveMode != UiModuleStoreMode.DATABASE) {
            call.respondRedirect(composeBundleLocation(mapOf("modeAccessError" to listOf("db-modules"))), permanent = false)
            return@get
        }
        call.respondRedirect(
            composeBundleLocation(
                linkedMapOf(
                    "screen" to listOf("module-editor"),
                    "storage" to listOf("database"),
                    "openCreate" to listOf("true"),
                ),
            ),
            permanent = false,
        )
    }
    get("/db-sync") {
        if (context.currentRuntimeContext().effectiveMode != UiModuleStoreMode.DATABASE) {
            call.respondRedirect(composeBundleLocation(mapOf("modeAccessError" to listOf("db-sync"))), permanent = false)
            return@get
        }
        call.respondRedirect(
            composeBundleLocation(linkedMapOf("screen" to listOf("module-sync"))),
            permanent = false,
        )
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

private fun composeBundleLocation(
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

private fun loadStaticBytes(
    resourcePath: String,
    classLoader: ClassLoader = UiConfigLoader::class.java.classLoader,
): ByteArray? = classLoader.getResourceAsStream(resourcePath)?.use { it.readBytes() }
