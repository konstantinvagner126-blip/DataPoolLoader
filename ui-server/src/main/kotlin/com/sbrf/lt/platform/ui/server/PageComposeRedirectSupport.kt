package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondRedirect
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
