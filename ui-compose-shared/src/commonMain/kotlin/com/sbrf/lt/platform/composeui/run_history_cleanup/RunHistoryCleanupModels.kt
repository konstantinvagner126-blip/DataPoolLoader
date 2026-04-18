package com.sbrf.lt.platform.composeui.run_history_cleanup

import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlinx.serialization.Serializable

@Serializable
data class RunHistoryCleanupModuleResponse(
    val moduleCode: String,
    val totalRunsToDelete: Int,
    val oldestRequestedAt: String? = null,
    val newestRequestedAt: String? = null,
)

@Serializable
data class RunHistoryCleanupPreviewResponse(
    val storageMode: String,
    val safeguardEnabled: Boolean,
    val retentionDays: Int,
    val keepMinRunsPerModule: Int,
    val cutoffTimestamp: String,
    val totalModulesAffected: Int = 0,
    val totalRunsToDelete: Int = 0,
    val totalSourceResultsToDelete: Int = 0,
    val totalEventsToDelete: Int = 0,
    val totalArtifactsToDelete: Int = 0,
    val totalOrphanExecutionSnapshotsToDelete: Int = 0,
    val modules: List<RunHistoryCleanupModuleResponse> = emptyList(),
)

@Serializable
data class RunHistoryCleanupRequestDto(
    val disableSafeguard: Boolean = false,
)

@Serializable
data class RunHistoryCleanupResultResponse(
    val storageMode: String,
    val safeguardEnabled: Boolean,
    val retentionDays: Int,
    val keepMinRunsPerModule: Int,
    val cutoffTimestamp: String,
    val finishedAt: String,
    val totalModulesAffected: Int = 0,
    val totalRunsDeleted: Int = 0,
    val totalSourceResultsDeleted: Int = 0,
    val totalEventsDeleted: Int = 0,
    val totalArtifactsDeleted: Int = 0,
    val totalOrphanExecutionSnapshotsDeleted: Int = 0,
    val modules: List<RunHistoryCleanupModuleResponse> = emptyList(),
)

@Serializable
data class OutputRetentionModuleResponse(
    val moduleCode: String,
    val totalRunsAffected: Int,
    val totalOutputDirsToDelete: Int,
    val totalBytesToFree: Long,
    val oldestRequestedAt: String? = null,
    val newestRequestedAt: String? = null,
)

@Serializable
data class OutputRetentionPreviewResponse(
    val storageMode: String,
    val safeguardEnabled: Boolean,
    val retentionDays: Int,
    val keepMinRunsPerModule: Int,
    val cutoffTimestamp: String,
    val totalModulesAffected: Int = 0,
    val totalRunsAffected: Int = 0,
    val totalOutputDirsToDelete: Int = 0,
    val totalMissingOutputDirs: Int = 0,
    val totalBytesToFree: Long = 0,
    val modules: List<OutputRetentionModuleResponse> = emptyList(),
)

@Serializable
data class OutputRetentionRequestDto(
    val disableSafeguard: Boolean = false,
)

@Serializable
data class OutputRetentionResultResponse(
    val storageMode: String,
    val safeguardEnabled: Boolean,
    val retentionDays: Int,
    val keepMinRunsPerModule: Int,
    val cutoffTimestamp: String,
    val finishedAt: String,
    val totalModulesAffected: Int = 0,
    val totalRunsAffected: Int = 0,
    val totalOutputDirsDeleted: Int = 0,
    val totalMissingOutputDirs: Int = 0,
    val totalBytesFreed: Long = 0,
    val modules: List<OutputRetentionModuleResponse> = emptyList(),
)

data class RunHistoryCleanupPageState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val actionInProgress: String? = null,
    val runtimeContext: RuntimeContext? = null,
    val cleanupDisableSafeguard: Boolean = false,
    val preview: RunHistoryCleanupPreviewResponse? = null,
    val outputDisableSafeguard: Boolean = false,
    val outputPreview: OutputRetentionPreviewResponse? = null,
)
