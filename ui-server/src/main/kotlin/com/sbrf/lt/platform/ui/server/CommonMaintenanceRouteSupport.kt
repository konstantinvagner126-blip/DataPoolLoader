package com.sbrf.lt.platform.ui.server

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.config.UiModuleStoreMode
import com.sbrf.lt.platform.ui.config.UiRuntimeContext
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupRequest
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupResultResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionPreviewResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionRequest
import com.sbrf.lt.platform.ui.model.output.OutputRetentionResultResponse

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
