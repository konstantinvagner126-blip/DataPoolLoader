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
import com.sbrf.lt.platform.ui.model.toDiagnosticsResponse
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
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMaintenanceIsInactive()
        context.requireDatabaseMode(runtimeContext)
        val cleanupService = requireNotNull(context.currentDatabaseRunHistoryCleanupService()) {
            "Сервис cleanup истории запусков для режима базы данных не настроен."
        }
        val disableSafeguard = context.includeHiddenQueryParam(call.request.queryParameters["disableSafeguard"])
        call.respond(cleanupService.previewCleanup(disableSafeguard = disableSafeguard))
    }

    post("/api/db/run-history/cleanup") {
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMaintenanceIsInactive()
        context.requireDatabaseMode(runtimeContext)
        val cleanupService = requireNotNull(context.currentDatabaseRunHistoryCleanupService()) {
            "Сервис cleanup истории запусков для режима базы данных не настроен."
        }
        val payload = call.receiveText()
        val request = try {
            if (payload.isBlank()) {
                DatabaseRunHistoryCleanupRequest()
            } else {
                mapper.readValue(payload, DatabaseRunHistoryCleanupRequest::class.java)
            }
        } catch (error: Exception) {
            logger.warn("Некорректный payload для /api/db/run-history/cleanup: {}", payload.take(4_000), error)
            throw IllegalArgumentException("Некорректные данные для cleanup истории запусков.")
        }
        call.respond(cleanupService.executeCleanup(disableSafeguard = request.disableSafeguard))
    }

    get("/api/db/sync/runs") {
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMode(runtimeContext)
        val syncService = requireNotNull(context.currentModuleSyncService()) {
            "Сервис импорта модулей в базу данных не настроен."
        }
        call.respond(mapOf("runs" to syncService.listSyncRuns(context.parseLimit(call.request.queryParameters["limit"]))))
    }

    get("/api/db/sync/runs/{syncRunId}") {
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMode(runtimeContext)
        val syncService = requireNotNull(context.currentModuleSyncService()) {
            "Сервис импорта модулей в базу данных не настроен."
        }
        val syncRunId = requireNotNull(call.parameters["syncRunId"])
        val details = requireNotNull(syncService.loadSyncRunDetails(syncRunId)) {
            "История импорта '$syncRunId' не найдена."
        }
        call.respond(details)
    }

    get("/api/db/modules/catalog") {
        context.requireDatabaseMaintenanceIsInactive()
        val runtimeContext = context.currentRuntimeContext()
        val store = context.currentDatabaseModuleStore()
        val includeHidden = context.includeHiddenQueryParam(call.request.queryParameters["includeHidden"])
        val modules = if (runtimeContext.effectiveMode == com.sbrf.lt.platform.ui.config.UiModuleStoreMode.DATABASE && store != null) {
            val activeModuleCodes = runCatching {
                context.currentDatabaseModuleRunService()?.activeModuleCodes().orEmpty()
            }.getOrDefault(emptySet())
            context.currentDatabaseModuleBackend()
                ?.listModules(includeHidden = includeHidden)
                ?.map { module ->
                    module.copy(hasActiveRun = module.id in activeModuleCodes)
                }
                .orEmpty()
        } else {
            emptyList()
        }
        call.respond(
            DatabaseModulesCatalogResponse(
                runtimeContext = runtimeContext,
                diagnostics = modules.toDiagnosticsResponse(),
                modules = modules,
            ),
        )
    }

    get("/api/db/modules/{id}") {
        context.requireDatabaseMaintenanceIsInactive()
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMode(runtimeContext)
        val backend = requireNotNull(context.currentDatabaseModuleBackend()) {
            "Сервис работы с модулями из базы данных не настроен."
        }
        call.respond(
            backend.loadModule(
                moduleId = requireNotNull(call.parameters["id"]),
                actor = context.requireDatabaseActor(runtimeContext),
            ),
        )
    }

    get("/api/db/modules/{id}/runs") {
        context.requireDatabaseMaintenanceIsInactive()
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMode(runtimeContext)
        val runService = requireNotNull(context.currentDatabaseModuleRunService()) {
            "Сервис истории запусков для режима базы данных не настроен."
        }
        call.respond(runService.listRuns(requireNotNull(call.parameters["id"])))
    }

    get("/api/db/modules/{id}/runs/{runId}") {
        context.requireDatabaseMaintenanceIsInactive()
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMode(runtimeContext)
        val runService = requireNotNull(context.currentDatabaseModuleRunService()) {
            "Сервис истории запусков для режима базы данных не настроен."
        }
        call.respond(
            runService.loadRunDetails(
                moduleCode = requireNotNull(call.parameters["id"]),
                runId = requireNotNull(call.parameters["runId"]),
            ),
        )
    }

    post("/api/db/modules/{id}/save") {
        context.requireDatabaseMaintenanceIsInactive()
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMode(runtimeContext)
        val moduleCode = requireNotNull(call.parameters["id"])
        context.requireDatabaseModuleIsNotSyncing(moduleCode)
        val backend = requireNotNull(context.currentDatabaseModuleBackend()) {
            "Сервис работы с модулями из базы данных не настроен."
        }
        backend.saveModule(
            moduleId = moduleCode,
            request = call.receive<SaveModuleRequest>(),
            actor = context.requireDatabaseActor(runtimeContext),
        )
        call.respond(SaveResultResponse("Изменения сохранены в личный черновик."))
    }

    post("/api/db/modules/{id}/discard-working-copy") {
        context.requireDatabaseMaintenanceIsInactive()
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMode(runtimeContext)
        val moduleCode = requireNotNull(call.parameters["id"])
        context.requireDatabaseModuleIsNotSyncing(moduleCode)
        val store = requireNotNull(context.currentDatabaseModuleStore()) {
            "Хранилище модулей из базы данных не настроено."
        }
        store.discardWorkingCopy(
            moduleCode = moduleCode,
            actorId = context.requireDatabaseActorId(runtimeContext),
            actorSource = context.requireDatabaseActorSource(runtimeContext),
        )
        call.respond(SaveResultResponse("Личный черновик удалён."))
    }

    post("/api/db/modules/{id}/publish") {
        context.requireDatabaseMaintenanceIsInactive()
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMode(runtimeContext)
        val moduleCode = requireNotNull(call.parameters["id"])
        context.requireDatabaseModuleIsNotSyncing(moduleCode)
        val store = requireNotNull(context.currentDatabaseModuleStore()) {
            "Хранилище модулей из базы данных не настроено."
        }
        val result = store.publishWorkingCopy(
            moduleCode = moduleCode,
            actorId = context.requireDatabaseActorId(runtimeContext),
            actorSource = context.requireDatabaseActorSource(runtimeContext),
            actorDisplayName = runtimeContext.actor.actorDisplayName,
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
        context.requireDatabaseMaintenanceIsInactive()
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMode(runtimeContext)
        val moduleCode = requireNotNull(call.parameters["id"])
        context.requireDatabaseModuleIsNotSyncing(moduleCode)
        val runService = requireNotNull(context.currentDatabaseModuleRunService()) {
            "Сервис запуска модулей из базы данных не настроен."
        }
        call.receive<DatabaseRunStartRequest>()
        call.respond(
            runService.startRun(
                moduleCode = moduleCode,
                actorId = context.requireDatabaseActorId(runtimeContext),
                actorSource = context.requireDatabaseActorSource(runtimeContext),
                actorDisplayName = runtimeContext.actor.actorDisplayName,
            ),
        )
    }

    post("/api/db/modules") {
        context.requireDatabaseMaintenanceIsInactive()
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMode(runtimeContext)
        val store = requireNotNull(context.currentDatabaseModuleStore()) {
            "Хранилище модулей из базы данных не настроено."
        }
        val request = call.receive<CreateDbModuleRequest>()
        val result = store.createModule(
            moduleCode = request.moduleCode,
            actorId = context.requireDatabaseActorId(runtimeContext),
            actorSource = context.requireDatabaseActorSource(runtimeContext),
            actorDisplayName = runtimeContext.actor.actorDisplayName,
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
        context.requireDatabaseMaintenanceIsInactive()
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMode(runtimeContext)
        val moduleCode = requireNotNull(call.parameters["id"])
        context.requireDatabaseModuleIsNotSyncing(moduleCode)
        val store = requireNotNull(context.currentDatabaseModuleStore()) {
            "Хранилище модулей из базы данных не настроено."
        }
        val result = store.deleteModule(
            moduleCode = moduleCode,
            actorId = context.requireDatabaseActorId(runtimeContext),
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
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMode(runtimeContext)
        val syncService = requireNotNull(context.currentModuleSyncService()) {
            "Сервис импорта модулей в базу данных не настроен."
        }
        val request = call.receive<SyncOneModuleRequest>()
        val result = syncService.syncOneFromFiles(
            moduleCode = request.moduleCode,
            appsRoot = context.currentAppsRootOrFail(),
            actorId = context.requireDatabaseActorId(runtimeContext),
            actorSource = runtimeContext.actor.actorSource ?: "OS_LOGIN",
            actorDisplayName = runtimeContext.actor.actorDisplayName,
        )
        call.respond(result)
    }

    post("/api/db/sync/selected") {
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMode(runtimeContext)
        val syncService = requireNotNull(context.currentModuleSyncService()) {
            "Сервис импорта модулей в базу данных не настроен."
        }
        val payload = call.receiveText()
        val request = try {
            mapper.readValue(payload, SyncSelectedModulesRequest::class.java)
        } catch (error: Exception) {
            logger.warn("Некорректный payload для /api/db/sync/selected: {}", payload.take(4_000), error)
            throw IllegalArgumentException("Некорректные данные для выборочной синхронизации.")
        }
        val result = syncService.syncSelectedFromFiles(
            moduleCodes = request.moduleCodes,
            appsRoot = context.currentAppsRootOrFail(),
            actorId = context.requireDatabaseActorId(runtimeContext),
            actorSource = runtimeContext.actor.actorSource ?: "OS_LOGIN",
            actorDisplayName = runtimeContext.actor.actorDisplayName,
        )
        call.respond(result)
    }

    post("/api/db/sync/all") {
        val runtimeContext = context.currentRuntimeContext()
        context.requireDatabaseMode(runtimeContext)
        val syncService = requireNotNull(context.currentModuleSyncService()) {
            "Сервис импорта модулей в базу данных не настроен."
        }
        val result = syncService.syncAllFromFiles(
            appsRoot = context.currentAppsRootOrFail(),
            actorId = context.requireDatabaseActorId(runtimeContext),
            actorSource = runtimeContext.actor.actorSource ?: "OS_LOGIN",
            actorDisplayName = runtimeContext.actor.actorDisplayName,
        )
        call.respond(result)
    }
}
