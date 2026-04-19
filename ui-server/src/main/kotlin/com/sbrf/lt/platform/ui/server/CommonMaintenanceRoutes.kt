package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

internal fun Route.registerCommonMaintenanceRoutes(
    context: UiServerContext,
    mapper: ObjectMapper,
) {
    get("/api/run-history/cleanup/preview") {
        val runtimeContext = context.currentRuntimeContext()
        val disableSafeguard = context.includeHiddenQueryParam(call.request.queryParameters["disableSafeguard"])
        call.respond(context.previewCommonRunHistoryCleanup(runtimeContext, disableSafeguard))
    }

    post("/api/run-history/cleanup") {
        val runtimeContext = context.currentRuntimeContext()
        val payload = call.receiveText()
        val request = context.parseCommonRunHistoryCleanupRequest(mapper, payload)
        call.respond(context.executeCommonRunHistoryCleanup(runtimeContext, request.disableSafeguard))
    }

    get("/api/output-retention/preview") {
        val runtimeContext = context.currentRuntimeContext()
        val disableSafeguard = context.includeHiddenQueryParam(call.request.queryParameters["disableSafeguard"])
        call.respond(context.previewCommonOutputRetention(runtimeContext, disableSafeguard))
    }

    post("/api/output-retention") {
        val runtimeContext = context.currentRuntimeContext()
        val payload = call.receiveText()
        val request = context.parseCommonOutputRetentionRequest(mapper, payload)
        call.respond(context.executeCommonOutputRetention(runtimeContext, request.disableSafeguard))
    }
}
