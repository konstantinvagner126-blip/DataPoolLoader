package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.ConfigFormUpdateRequest
import com.sbrf.lt.platform.ui.model.ModulesCatalogResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupRequest
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.model.SaveResultResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupResultResponse
import com.sbrf.lt.platform.ui.model.UiRuntimeModeUpdateRequest
import com.sbrf.lt.platform.ui.model.UiRuntimeModeUpdateResponse
import com.sbrf.lt.platform.ui.model.toDiagnosticsResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionPreviewResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionRequest
import com.sbrf.lt.platform.ui.model.output.OutputRetentionResultResponse
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import io.ktor.http.content.PartData
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import org.slf4j.Logger

internal fun UiServerContext.previewCommonRunHistoryCleanup(
    runtimeContext: UiRuntimeContext,
    disableSafeguard: Boolean,
): RunHistoryCleanupPreviewResponse =
    when (runtimeContext.requestedMode) {
        UiModuleStoreMode.FILES -> currentFilesRunHistoryCleanupService().previewCleanup(disableSafeguard)
        UiModuleStoreMode.DATABASE -> {
            requireDatabaseMaintenanceIsInactive()
            requireDatabaseMode(runtimeContext)
            requireNotNull(currentDatabaseRunHistoryCleanupService()) {
                "Сервис cleanup истории запусков для режима базы данных не настроен."
            }.previewCleanup(disableSafeguard).toCommonResponse()
        }
    }

internal fun UiServerContext.executeCommonRunHistoryCleanup(
    runtimeContext: UiRuntimeContext,
    disableSafeguard: Boolean,
): RunHistoryCleanupResultResponse =
    when (runtimeContext.requestedMode) {
        UiModuleStoreMode.FILES -> currentFilesRunHistoryCleanupService().executeCleanup(disableSafeguard)
        UiModuleStoreMode.DATABASE -> {
            requireDatabaseMaintenanceIsInactive()
            requireDatabaseMode(runtimeContext)
            requireNotNull(currentDatabaseRunHistoryCleanupService()) {
                "Сервис cleanup истории запусков для режима базы данных не настроен."
            }.executeCleanup(disableSafeguard).toCommonResponse()
        }
    }

internal fun UiServerContext.previewCommonOutputRetention(
    runtimeContext: UiRuntimeContext,
    disableSafeguard: Boolean,
): OutputRetentionPreviewResponse =
    when (runtimeContext.requestedMode) {
        UiModuleStoreMode.FILES -> currentFilesOutputRetentionService().previewCleanup(disableSafeguard)
        UiModuleStoreMode.DATABASE -> {
            requireDatabaseMaintenanceIsInactive()
            requireDatabaseMode(runtimeContext)
            requireNotNull(currentDatabaseOutputRetentionService()) {
                "Сервис retention output-каталогов для режима базы данных не настроен."
            }.previewCleanup(disableSafeguard)
        }
    }

internal fun UiServerContext.executeCommonOutputRetention(
    runtimeContext: UiRuntimeContext,
    disableSafeguard: Boolean,
): OutputRetentionResultResponse =
    when (runtimeContext.requestedMode) {
        UiModuleStoreMode.FILES -> currentFilesOutputRetentionService().executeCleanup(disableSafeguard)
        UiModuleStoreMode.DATABASE -> {
            requireDatabaseMaintenanceIsInactive()
            requireDatabaseMode(runtimeContext)
            requireNotNull(currentDatabaseOutputRetentionService()) {
                "Сервис retention output-каталогов для режима базы данных не настроен."
            }.executeCleanup(disableSafeguard)
        }
    }

internal data class UploadedCredentialsPayload(
    val fileName: String,
    val content: String,
)

internal fun UiServerContext.updateCommonRuntimeMode(
    request: UiRuntimeModeUpdateRequest,
): UiRuntimeModeUpdateResponse {
    val updatedConfig = uiConfigPersistenceService.updateModuleStoreMode(request.mode)
    val updatedRuntimeContext = resolveRuntimeContextFromConfig(updatedConfig)
    return UiRuntimeModeUpdateResponse(
        message = "Предпочитаемый режим UI сохранен: ${request.mode.toConfigValue()}.",
        runtimeContext = updatedRuntimeContext,
    )
}

internal fun UiServerContext.buildFilesModulesCatalogResponse(): ModulesCatalogResponse {
    val modules = filesModuleBackend.listModules()
    val activeModuleId = filesRunService.currentState().activeRun?.moduleId
    return ModulesCatalogResponse(
        appsRootStatus = requireNotNull(filesModuleBackend.catalogStatus()),
        diagnostics = modules.toDiagnosticsResponse(),
        modules = modules.map { module ->
            module.copy(hasActiveRun = module.id == activeModuleId)
        },
    )
}

internal fun UiServerContext.saveFilesModule(
    moduleId: String,
    request: SaveModuleRequest,
): SaveResultResponse {
    filesModuleBackend.saveModule(moduleId = moduleId, request = request)
    return SaveResultResponse("Изменения модуля сохранены.")
}

internal fun UiServerContext.applyCommonConfigFormUpdate(
    mapper: ObjectMapper,
    payload: String,
    logger: Logger,
) = parseCommonConfigFormUpdateRequest(mapper, payload, logger).let { request ->
    configFormService.apply(request.configText, request.formState)
}

internal fun UiServerContext.parseCommonRunHistoryCleanupRequest(
    mapper: ObjectMapper,
    payload: String,
): RunHistoryCleanupRequest =
    try {
        if (payload.isBlank()) {
            RunHistoryCleanupRequest()
        } else {
            mapper.readValue(payload, RunHistoryCleanupRequest::class.java)
        }
    } catch (_: Exception) {
        badRequest("Некорректные данные для cleanup истории запусков.")
    }

internal fun UiServerContext.parseCommonOutputRetentionRequest(
    mapper: ObjectMapper,
    payload: String,
): OutputRetentionRequest =
    try {
        if (payload.isBlank()) {
            OutputRetentionRequest()
        } else {
            mapper.readValue(payload, OutputRetentionRequest::class.java)
        }
    } catch (_: Exception) {
        badRequest("Некорректные данные для retention output-каталогов.")
    }

internal suspend fun ApplicationCall.readUploadedCredentialsPayload(): UploadedCredentialsPayload {
    val multipart = receiveMultipart()
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
    return UploadedCredentialsPayload(
        fileName = fileName,
        content = content?.takeIf { it.isNotBlank() }
            ?: badRequest("Не удалось прочитать содержимое credential.properties."),
    )
}

private fun UiServerContext.parseCommonConfigFormUpdateRequest(
    mapper: ObjectMapper,
    payload: String,
    logger: Logger,
): ConfigFormUpdateRequest =
    try {
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

private fun com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupPreviewResponse.toCommonResponse(): RunHistoryCleanupPreviewResponse =
    RunHistoryCleanupPreviewResponse(
        storageMode = "DATABASE",
        safeguardEnabled = safeguardEnabled,
        retentionDays = retentionDays,
        keepMinRunsPerModule = keepMinRunsPerModule,
        cutoffTimestamp = cutoffTimestamp,
        currentRunsCount = currentRunsCount,
        currentModulesCount = currentModulesCount,
        currentStorageBytes = currentStorageBytes,
        currentOldestRequestedAt = currentOldestRequestedAt,
        currentNewestRequestedAt = currentNewestRequestedAt,
        currentTopModules = currentTopModules.map { module ->
            com.sbrf.lt.platform.ui.model.CurrentStorageModuleResponse(
                moduleCode = module.moduleCode,
                currentRunsCount = module.currentRunsCount,
                currentStorageBytes = module.currentStorageBytes,
                currentOutputDirs = module.currentOutputDirs,
                oldestRequestedAt = module.oldestRequestedAt,
                newestRequestedAt = module.newestRequestedAt,
            )
        },
        estimatedBytesToFree = estimatedBytesToFree,
        totalModulesAffected = totalModulesAffected,
        totalRunsToDelete = totalRunsToDelete,
        totalSourceResultsToDelete = totalSourceResultsToDelete,
        totalEventsToDelete = totalEventsToDelete,
        totalArtifactsToDelete = totalArtifactsToDelete,
        totalOrphanExecutionSnapshotsToDelete = totalOrphanExecutionSnapshotsToDelete,
        modules = modules.map { module ->
            com.sbrf.lt.platform.ui.model.RunHistoryCleanupModuleResponse(
                moduleCode = module.moduleCode,
                totalRunsToDelete = module.totalRunsToDelete,
                oldestRequestedAt = module.oldestRequestedAt,
                newestRequestedAt = module.newestRequestedAt,
            )
        },
    )

private fun com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse.toCommonResponse(): RunHistoryCleanupResultResponse =
    RunHistoryCleanupResultResponse(
        storageMode = "DATABASE",
        safeguardEnabled = safeguardEnabled,
        retentionDays = retentionDays,
        keepMinRunsPerModule = keepMinRunsPerModule,
        cutoffTimestamp = cutoffTimestamp,
        finishedAt = finishedAt,
        totalModulesAffected = totalModulesAffected,
        totalRunsDeleted = totalRunsDeleted,
        totalSourceResultsDeleted = totalSourceResultsDeleted,
        totalEventsDeleted = totalEventsDeleted,
        totalArtifactsDeleted = totalArtifactsDeleted,
        totalOrphanExecutionSnapshotsDeleted = totalOrphanExecutionSnapshotsDeleted,
        modules = modules.map { module ->
            com.sbrf.lt.platform.ui.model.RunHistoryCleanupModuleResponse(
                moduleCode = module.moduleCode,
                totalRunsToDelete = module.totalRunsToDelete,
                oldestRequestedAt = module.oldestRequestedAt,
                newestRequestedAt = module.newestRequestedAt,
            )
        },
    )
