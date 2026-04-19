package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.CreateDbModuleRequest
import com.sbrf.lt.platform.ui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunStartRequest
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.model.SaveResultResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupRequest
import com.sbrf.lt.platform.ui.model.SyncOneModuleRequest
import com.sbrf.lt.platform.ui.model.SyncSelectedModulesRequest
import com.sbrf.lt.platform.ui.sync.ModuleSyncRunNotFoundException
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

/**
 * API DB-режима: каталог модулей, личный черновик, запуск и import-flow.
 */
internal fun Route.registerDatabaseRoutes(
    context: UiServerContext,
    mapper: ObjectMapper,
) {
    val logger = LoggerFactory.getLogger("DatabaseRoutes")
    get("/api/db/sync/state") {
        call.respond(context.readSyncStateSafely())
    }

    get("/api/db/run-history/cleanup/preview") {
        val disableSafeguard = context.includeHiddenQueryParam(call.request.queryParameters["disableSafeguard"])
        call.respond(context.previewDatabaseRunHistoryCleanup(disableSafeguard))
    }

    post("/api/db/run-history/cleanup") {
        val payload = call.receiveText()
        val request = try {
            if (payload.isBlank()) {
                DatabaseRunHistoryCleanupRequest()
            } else {
                mapper.readValue(payload, DatabaseRunHistoryCleanupRequest::class.java)
            }
        } catch (error: Exception) {
            logger.warn("Некорректный payload для /api/db/run-history/cleanup: {}", payload.take(4_000), error)
            badRequest("Некорректные данные для cleanup истории запусков.")
        }
        call.respond(context.executeDatabaseRunHistoryCleanup(request.disableSafeguard))
    }

    get("/api/db/sync/runs") {
        call.respond(mapOf("runs" to context.listDatabaseSyncRuns(context.parseLimit(call.request.queryParameters["limit"]))))
    }

    get("/api/db/sync/runs/{syncRunId}") {
        val syncRunId = call.requireRouteParam("syncRunId")
        val details = context.loadDatabaseSyncRunDetails(syncRunId) ?: throw ModuleSyncRunNotFoundException(syncRunId)
        call.respond(details)
    }

    get("/api/db/modules/catalog") {
        val includeHidden = context.includeHiddenQueryParam(call.request.queryParameters["includeHidden"])
        call.respond(context.buildDatabaseModulesCatalogResponse(includeHidden))
    }

    get("/api/db/modules/{id}") {
        val routeContext = context.requireDatabaseModuleRouteContext(call)
        val backend = context.requireDatabaseRouteBackend(routeContext.runtimeContext)
        call.respond(
            backend.loadModule(
                moduleId = routeContext.moduleCode,
                actor = context.requireDatabaseActor(routeContext.runtimeContext),
            ),
        )
    }

    get("/api/db/modules/{id}/runs") {
        val routeContext = context.requireDatabaseModuleRouteContext(call)
        val runService = context.requireDatabaseRouteRunService(routeContext.runtimeContext)
        call.respond(runService.listRuns(routeContext.moduleCode))
    }

    get("/api/db/modules/{id}/runs/{runId}") {
        val routeContext = context.requireDatabaseModuleRouteContext(call)
        val runService = context.requireDatabaseRouteRunService(routeContext.runtimeContext)
        call.respond(
            runService.loadRunDetails(
                moduleCode = routeContext.moduleCode,
                runId = call.requireRouteParam("runId"),
            ),
        )
    }

    post("/api/db/modules/{id}/save") {
        val routeContext = context.requireDatabaseMutableModuleRouteContext(call)
        val backend = context.requireDatabaseRouteBackend(routeContext.runtimeContext)
        backend.saveModule(
            moduleId = routeContext.moduleCode,
            request = call.receive<SaveModuleRequest>(),
            actor = context.requireDatabaseActor(routeContext.runtimeContext),
        )
        call.respond(SaveResultResponse("Изменения сохранены в личный черновик."))
    }

    post("/api/db/modules/{id}/discard-working-copy") {
        val routeContext = context.requireDatabaseMutableModuleRouteContext(call)
        val store = context.requireDatabaseRouteStore(routeContext.runtimeContext)
        store.discardWorkingCopy(
            moduleCode = routeContext.moduleCode,
            actorId = routeContext.actor.actorId,
            actorSource = routeContext.actor.actorSource,
        )
        call.respond(SaveResultResponse("Личный черновик удалён."))
    }

    post("/api/db/modules/{id}/publish") {
        val routeContext = context.requireDatabaseMutableModuleRouteContext(call)
        val store = context.requireDatabaseRouteStore(routeContext.runtimeContext)
        val result = store.publishWorkingCopy(
            moduleCode = routeContext.moduleCode,
            actorId = routeContext.actor.actorId,
            actorSource = routeContext.actor.actorSource,
            actorDisplayName = routeContext.actor.actorDisplayName,
        )
        call.respond(
            mapOf(
                "message" to "Черновик опубликован как ревизия #${result.revisionNo}.",
                "revisionId" to result.revisionId,
                "revisionNo" to result.revisionNo,
                "moduleCode" to result.moduleCode,
            ),
        )
    }

    post("/api/db/modules/{id}/run") {
        val routeContext = context.requireDatabaseMutableModuleRouteContext(call)
        val runService = context.requireDatabaseRouteRunService(routeContext.runtimeContext)
        call.receive<DatabaseRunStartRequest>()
        call.respond(
            runService.startRun(
                moduleCode = routeContext.moduleCode,
                actorId = routeContext.actor.actorId,
                actorSource = routeContext.actor.actorSource,
                actorDisplayName = routeContext.actor.actorDisplayName,
            ),
        )
    }

    post("/api/db/modules") {
        val runtimeContext = context.requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
        val store = context.requireDatabaseRouteStore(runtimeContext)
        val actor = context.requireDatabaseRouteActor(runtimeContext)
        val request = call.receive<CreateDbModuleRequest>()
        val result = store.createModule(
            moduleCode = request.moduleCode,
            actorId = actor.actorId,
            actorSource = actor.actorSource,
            actorDisplayName = actor.actorDisplayName,
            request = com.sbrf.lt.platform.ui.module.CreateModuleRequest(
                title = request.title,
                description = request.description,
                tags = request.tags,
                configText = request.configText,
                hiddenFromUi = request.hiddenFromUi,
            ),
        )
        call.respond(
            mapOf(
                "message" to "Модуль '${result.moduleCode}' создан в базе данных.",
                "moduleId" to result.moduleId,
                "moduleCode" to result.moduleCode,
                "revisionId" to result.revisionId,
                "workingCopyId" to result.workingCopyId,
            ),
        )
    }

    delete("/api/db/modules/{id}") {
        val routeContext = context.requireDatabaseMutableModuleRouteContext(call)
        val store = context.requireDatabaseRouteStore(routeContext.runtimeContext)
        val result = store.deleteModule(
            moduleCode = routeContext.moduleCode,
            actorId = routeContext.actor.actorId,
        )
        call.respond(
            mapOf(
                "message" to "Модуль '${result.moduleCode}' удалён из базы данных.",
                "moduleCode" to result.moduleCode,
                "deletedBy" to result.deletedBy,
            ),
        )
    }

    post("/api/db/sync/one") {
        val request = call.receive<SyncOneModuleRequest>()
        call.respond(context.syncOneDatabaseModuleFromFiles(request))
    }

    post("/api/db/sync/selected") {
        val payload = call.receiveText()
        val request = try {
            mapper.readValue(payload, SyncSelectedModulesRequest::class.java)
        } catch (error: Exception) {
            logger.warn("Некорректный payload для /api/db/sync/selected: {}", payload.take(4_000), error)
            badRequest("Некорректные данные для выборочной синхронизации.")
        }
        call.respond(context.syncSelectedDatabaseModulesFromFiles(request))
    }

    post("/api/db/sync/all") {
        call.respond(context.syncAllDatabaseModulesFromFiles())
    }
}
