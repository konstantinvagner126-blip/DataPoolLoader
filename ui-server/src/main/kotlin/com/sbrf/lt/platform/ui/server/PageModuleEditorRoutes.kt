package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import io.ktor.server.routing.Route

internal fun Route.registerPageModuleEditorRoutes(context: UiServerContext) {
    registerModeGuardedComposeRedirect(
        context = context,
        path = "/modules",
        expectedMode = UiModuleStoreMode.FILES,
        modeAccessError = "modules",
        "screen" to "module-editor",
        "storage" to "files",
        forwardedParams = listOf("module"),
    )
    registerModeGuardedComposeRedirect(
        context = context,
        path = "/db-modules",
        expectedMode = UiModuleStoreMode.DATABASE,
        modeAccessError = "db-modules",
        "screen" to "module-editor",
        "storage" to "database",
        forwardedParams = listOf("module", "includeHidden"),
    )
    registerModeGuardedComposeRedirect(
        context = context,
        path = "/db-modules/new",
        expectedMode = UiModuleStoreMode.DATABASE,
        modeAccessError = "db-modules",
        "screen" to "module-editor",
        "storage" to "database",
        "openCreate" to "true",
    )
    registerModeGuardedComposeRedirect(
        context = context,
        path = "/db-sync",
        expectedMode = UiModuleStoreMode.DATABASE,
        modeAccessError = "db-sync",
        "screen" to "module-sync",
    )
}
