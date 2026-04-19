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
        val runtimeContext = context.requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
        val cleanupService = context.requireDatabaseRouteCleanupService(runtimeContext)
        val disableSafeguard = context.includeHiddenQueryParam(call.request.queryParameters["disableSafeguard"])
        call.respond(cleanupService.previewCleanup(disableSafeguard = disableSafeguard))
    }

    post("/api/db/run-history/cleanup") {
        val runtimeContext = context.requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
        val cleanupService = context.requireDatabaseRouteCleanupService(runtimeContext)
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
        call.respond(cleanupService.executeCleanup(disableSafeguard = request.disableSafeguard))
    }

    get("/api/db/sync/runs") {
        val runtimeContext = context.requireDatabaseRuntimeContext()
        val syncService = context.requireDatabaseRouteSyncService(runtimeContext)
        call.respond(mapOf("runs" to syncService.listSyncRuns(context.parseLimit(call.request.queryParameters["limit"]))))
    }

    get("/api/db/sync/runs/{syncRunId}") {
        val runtimeContext = context.requireDatabaseRuntimeContext()
        val syncService = context.requireDatabaseRouteSyncService(runtimeContext)
        val syncRunId = requireNotNull(call.parameters["syncRunId"])
        val details = requireNotNull(syncService.loadSyncRunDetails(syncRunId)) {
            "История импорта '$syncRunId' не найдена."
        }
        call.respond(details)
    }

    get("/api/db/modules/catalog") {
        val includeHidden = context.includeHiddenQueryParam(call.request.queryParameters["includeHidden"])
        call.respond(context.buildDatabaseModulesCatalogResponse(includeHidden))
    }

    get("/api/db/modules/{id}") {
        val runtimeContext = context.requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
        val backend = context.requireDatabaseRouteBackend(runtimeContext)
        call.respond(
            backend.loadModule(
                moduleId = requireNotNull(call.parameters["id"]),
                actor = context.requireDatabaseActor(runtimeContext),
            ),
        )
    }

    get("/api/db/modules/{id}/runs") {
        val runtimeContext = context.requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
        val runService = context.requireDatabaseRouteRunService(runtimeContext)
        call.respond(runService.listRuns(requireNotNull(call.parameters["id"])))
    }

    get("/api/db/modules/{id}/runs/{runId}") {
        val runtimeContext = context.requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
        val runService = context.requireDatabaseRouteRunService(runtimeContext)
        call.respond(
            runService.loadRunDetails(
                moduleCode = requireNotNull(call.parameters["id"]),
                runId = requireNotNull(call.parameters["runId"]),
            ),
        )
    }

    post("/api/db/modules/{id}/save") {
        val runtimeContext = context.requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
        val moduleCode = requireNotNull(call.parameters["id"])
        context.requireDatabaseModuleIsNotSyncing(moduleCode)
        val backend = context.requireDatabaseRouteBackend(runtimeContext)
        backend.saveModule(
            moduleId = moduleCode,
            request = call.receive<SaveModuleRequest>(),
            actor = context.requireDatabaseActor(runtimeContext),
        )
        call.respond(SaveResultResponse("Изменения сохранены в личный черновик."))
    }

    post("/api/db/modules/{id}/discard-working-copy") {
        val runtimeContext = context.requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
        val moduleCode = requireNotNull(call.parameters["id"])
        context.requireDatabaseModuleIsNotSyncing(moduleCode)
        val store = context.requireDatabaseRouteStore(runtimeContext)
        val actor = context.requireDatabaseRouteActor(runtimeContext)
        store.discardWorkingCopy(
            moduleCode = moduleCode,
            actorId = actor.actorId,
            actorSource = actor.actorSource,
        )
        call.respond(SaveResultResponse("Личный черновик удалён."))
    }

    post("/api/db/modules/{id}/publish") {
        val runtimeContext = context.requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
        val moduleCode = requireNotNull(call.parameters["id"])
        context.requireDatabaseModuleIsNotSyncing(moduleCode)
        val store = context.requireDatabaseRouteStore(runtimeContext)
        val actor = context.requireDatabaseRouteActor(runtimeContext)
        val result = store.publishWorkingCopy(
            moduleCode = moduleCode,
            actorId = actor.actorId,
            actorSource = actor.actorSource,
            actorDisplayName = actor.actorDisplayName,
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
        val runtimeContext = context.requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
        val moduleCode = requireNotNull(call.parameters["id"])
        context.requireDatabaseModuleIsNotSyncing(moduleCode)
        val runService = context.requireDatabaseRouteRunService(runtimeContext)
        val actor = context.requireDatabaseRouteActor(runtimeContext)
        call.receive<DatabaseRunStartRequest>()
        call.respond(
            runService.startRun(
                moduleCode = moduleCode,
                actorId = actor.actorId,
                actorSource = actor.actorSource,
                actorDisplayName = actor.actorDisplayName,
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
        val runtimeContext = context.requireDatabaseRuntimeContext(requireMaintenanceInactive = true)
        val moduleCode = requireNotNull(call.parameters["id"])
        context.requireDatabaseModuleIsNotSyncing(moduleCode)
        val store = context.requireDatabaseRouteStore(runtimeContext)
        val actor = context.requireDatabaseRouteActor(runtimeContext)
        val result = store.deleteModule(
            moduleCode = moduleCode,
            actorId = actor.actorId,
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
        val runtimeContext = context.requireDatabaseRuntimeContext()
        val syncService = context.requireDatabaseRouteSyncService(runtimeContext)
        val actor = context.requireDatabaseRouteSyncActor(runtimeContext)
        val request = call.receive<SyncOneModuleRequest>()
        val result = syncService.syncOneFromFiles(
            moduleCode = request.moduleCode,
            appsRoot = context.currentAppsRootOrFail(),
            actorId = actor.actorId,
            actorSource = actor.actorSource,
            actorDisplayName = actor.actorDisplayName,
        )
        call.respond(result)
    }

    post("/api/db/sync/selected") {
        val runtimeContext = context.requireDatabaseRuntimeContext()
        val syncService = context.requireDatabaseRouteSyncService(runtimeContext)
        val actor = context.requireDatabaseRouteSyncActor(runtimeContext)
        val payload = call.receiveText()
        val request = try {
            mapper.readValue(payload, SyncSelectedModulesRequest::class.java)
        } catch (error: Exception) {
            logger.warn("Некорректный payload для /api/db/sync/selected: {}", payload.take(4_000), error)
            badRequest("Некорректные данные для выборочной синхронизации.")
        }
        val result = syncService.syncSelectedFromFiles(
            moduleCodes = request.moduleCodes,
            appsRoot = context.currentAppsRootOrFail(),
            actorId = actor.actorId,
            actorSource = actor.actorSource,
            actorDisplayName = actor.actorDisplayName,
        )
        call.respond(result)
    }

    post("/api/db/sync/all") {
        val runtimeContext = context.requireDatabaseRuntimeContext()
        val syncService = context.requireDatabaseRouteSyncService(runtimeContext)
        val actor = context.requireDatabaseRouteSyncActor(runtimeContext)
        val result = syncService.syncAllFromFiles(
            appsRoot = context.currentAppsRootOrFail(),
            actorId = actor.actorId,
            actorSource = actor.actorSource,
            actorDisplayName = actor.actorDisplayName,
        )
        call.respond(result)
    }
}
