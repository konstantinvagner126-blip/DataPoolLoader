package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.ConfigFormParseRequest
import com.sbrf.lt.platform.ui.model.ConfigFormUpdateRequest
import com.sbrf.lt.platform.ui.model.ModulesCatalogResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupRequest
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.model.SaveResultResponse
import com.sbrf.lt.platform.ui.model.StartRunRequest
import com.sbrf.lt.platform.ui.model.UiRuntimeModeUpdateRequest
import com.sbrf.lt.platform.ui.model.UiRuntimeModeUpdateResponse
import com.sbrf.lt.platform.ui.model.toDiagnosticsResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionRequest
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.collect
import org.slf4j.LoggerFactory

/**
 * Общие UI API: runtime mode, файловые модули, config-form, credentials и websocket.
 */
internal fun Route.registerCommonRoutes(
    context: UiServerContext,
    mapper: ObjectMapper,
) {
    val logger = LoggerFactory.getLogger("ConfigFormRoutes")
    get("/api/modules") {
        call.respond(context.filesModuleBackend.listModules())
    }

    get("/api/ui/runtime-context") {
        call.respond(context.currentRuntimeContext())
    }

    post("/api/ui/runtime-mode") {
        val request = call.receive<UiRuntimeModeUpdateRequest>()
        val updatedConfig = context.uiConfigPersistenceService.updateModuleStoreMode(request.mode)
        val updatedRuntimeContext = context.resolveRuntimeContextFromConfig(updatedConfig)
        call.respond(
            UiRuntimeModeUpdateResponse(
                message = "Предпочитаемый режим UI сохранен: ${request.mode.toConfigValue()}.",
                runtimeContext = updatedRuntimeContext,
            ),
        )
    }

    get("/api/modules/catalog") {
        val modules = context.filesModuleBackend.listModules()
        val activeModuleId = context.filesRunService.currentState().activeRun?.moduleId
        call.respond(
            ModulesCatalogResponse(
                appsRootStatus = requireNotNull(context.filesModuleBackend.catalogStatus()),
                diagnostics = modules.toDiagnosticsResponse(),
                modules = modules.map { module ->
                    module.copy(hasActiveRun = module.id == activeModuleId)
                },
            ),
        )
    }

    get("/api/modules/{id}") {
        call.respond(context.filesModuleBackend.loadModule(requireNotNull(call.parameters["id"])))
    }

    post("/api/modules/{id}/save") {
        context.filesModuleBackend.saveModule(
            moduleId = requireNotNull(call.parameters["id"]),
            request = call.receive<SaveModuleRequest>(),
        )
        call.respond(SaveResultResponse("Изменения модуля сохранены."))
    }

    post("/api/config-form/parse") {
        val request = call.receive<ConfigFormParseRequest>()
        call.respond(context.configFormService.parse(request.configText))
    }

    post("/api/config-form/update") {
        val payload = call.receiveText()
        val request = try {
            mapper.readValue(payload, ConfigFormUpdateRequest::class.java)
        } catch (error: Exception) {
            logger.warn("Некорректный payload для /api/config-form/update: {}", payload.take(4_000), error)
            val rootCauseMessage = generateSequence<Throwable>(error) { it.cause }
                .lastOrNull()
                ?.message
                ?.takeIf { it.isNotBlank() }
            badRequest(
                buildString {
                    append("Некорректные данные формы настроек.")
                    if (!rootCauseMessage.isNullOrBlank()) {
                        append(" ")
                        append(rootCauseMessage)
                    }
                },
            )
        }
        call.respond(context.configFormService.apply(request.configText, request.formState))
    }

    post("/api/runs") {
        call.respond(context.filesRunService.startRun(call.receive<StartRunRequest>()))
    }

    get("/api/state") {
        call.respond(context.filesRunService.currentState())
    }

    get("/api/credentials") {
        call.respond(context.filesRunService.currentCredentialsStatus())
    }

    get("/api/run-history/cleanup/preview") {
        val runtimeContext = context.currentRuntimeContext()
        val disableSafeguard = context.includeHiddenQueryParam(call.request.queryParameters["disableSafeguard"])
        call.respond(context.previewCommonRunHistoryCleanup(runtimeContext, disableSafeguard))
    }

    post("/api/run-history/cleanup") {
        val runtimeContext = context.currentRuntimeContext()
        val payload = call.receiveText()
        val request = try {
            if (payload.isBlank()) {
                RunHistoryCleanupRequest()
            } else {
                mapper.readValue(payload, RunHistoryCleanupRequest::class.java)
            }
        } catch (_: Exception) {
            badRequest("Некорректные данные для cleanup истории запусков.")
        }
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
        val request = try {
            if (payload.isBlank()) {
                OutputRetentionRequest()
            } else {
                mapper.readValue(payload, OutputRetentionRequest::class.java)
            }
        } catch (_: Exception) {
            badRequest("Некорректные данные для retention output-каталогов.")
        }
        call.respond(context.executeCommonOutputRetention(runtimeContext, request.disableSafeguard))
    }

    post("/api/credentials/upload") {
        val multipart = call.receiveMultipart()
        var fileName = "credential.properties"
        var content: String? = null
        while (true) {
            val part = multipart.readPart() ?: break
            when (part) {
                is PartData.FileItem -> {
                    fileName = part.originalFileName ?: fileName
                    content = part.provider().readRemaining().readText()
                }
                else -> Unit
            }
            part.dispose.invoke()
        }
        if (content.isNullOrBlank()) {
            badRequest("Не удалось прочитать содержимое credential.properties.")
        }
        call.respond(context.filesRunService.uploadCredentials(fileName, requireNotNull(content)))
    }

    webSocket("/ws") {
        send(Frame.Text(mapper.writeValueAsString(context.filesRunService.currentState())))
        context.filesRunService.updates().collect { state ->
            send(Frame.Text(mapper.writeValueAsString(state)))
        }
    }
}
