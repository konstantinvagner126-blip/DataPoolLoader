package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Статические страницы UI и guards доступа по текущему режиму.
 */
internal fun Route.registerPageRoutes(context: UiServerContext) {
    get("/") {
        val content = loadStaticText("static/home.html")
        call.respondText(content, ContentType.Text.Html)
    }
    get("/modules") {
        if (context.currentRuntimeContext().effectiveMode != UiModuleStoreMode.FILES) {
            call.respondRedirect("/?modeAccessError=modules", permanent = false)
            return@get
        }
        call.respondText(loadStaticText("static/index.html"), ContentType.Text.Html)
    }
    get("/help") {
        call.respondText(loadStaticText("static/help.html"), ContentType.Text.Html)
    }
    get("/help/api") {
        call.respondText(loadStaticText("static/help-api.html"), ContentType.Text.Html)
    }
    get("/module-runs") {
        when (call.request.queryParameters["storage"]?.lowercase()) {
            "files" -> {
                if (context.currentRuntimeContext().effectiveMode != UiModuleStoreMode.FILES) {
                    call.respondRedirect("/?modeAccessError=modules", permanent = false)
                    return@get
                }
            }
            "database" -> {
                if (context.currentRuntimeContext().effectiveMode != UiModuleStoreMode.DATABASE) {
                    call.respondRedirect("/?modeAccessError=db-modules", permanent = false)
                    return@get
                }
            }
            else -> {
                call.respondRedirect("/", permanent = false)
                return@get
            }
        }
        call.respondText(loadStaticText("static/module-runs.html"), ContentType.Text.Html)
    }
    get("/sql-console") {
        call.respondText(loadStaticText("static/sql-console.html"), ContentType.Text.Html)
    }
    get("/db-modules") {
        if (context.currentRuntimeContext().effectiveMode != UiModuleStoreMode.DATABASE) {
            call.respondRedirect("/?modeAccessError=db-modules", permanent = false)
            return@get
        }
        call.respondText(loadStaticText("static/db-modules.html"), ContentType.Text.Html)
    }
    get("/db-modules/new") {
        if (context.currentRuntimeContext().effectiveMode != UiModuleStoreMode.DATABASE) {
            call.respondRedirect("/?modeAccessError=db-modules", permanent = false)
            return@get
        }
        call.respondText(loadStaticText("static/db-module-create.html"), ContentType.Text.Html)
    }
    get("/db-sync") {
        if (context.currentRuntimeContext().effectiveMode != UiModuleStoreMode.DATABASE) {
            call.respondRedirect("/?modeAccessError=db-sync", permanent = false)
            return@get
        }
        call.respondText(loadStaticText("static/db-sync.html"), ContentType.Text.Html)
    }
    staticResources("/static", "static")
}
