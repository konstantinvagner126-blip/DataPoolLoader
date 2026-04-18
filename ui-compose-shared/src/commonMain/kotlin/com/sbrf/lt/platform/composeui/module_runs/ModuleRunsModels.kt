package com.sbrf.lt.platform.composeui.module_runs

import kotlinx.serialization.Serializable

@Serializable
data class ModuleRunPageSessionResponse(
    val storageMode: String,
    val moduleId: String,
    val moduleTitle: String,
    val moduleMeta: String,
)

@Serializable
data class UiSettingsResponse(
    val showTechnicalDiagnostics: Boolean = true,
    val showRawSummaryJson: Boolean = false,
)

@Serializable
data class ModuleRunHistoryResponse(
    val storageMode: String,
    val moduleId: String,
    val activeRunId: String? = null,
    val uiSettings: UiSettingsResponse = UiSettingsResponse(),
    val runs: List<ModuleRunSummaryResponse> = emptyList(),
)

@Serializable
data class ModuleRunSummaryResponse(
    val runId: String,
    val moduleId: String,
    val moduleTitle: String,
    val status: String,
    val startedAt: String,
    val finishedAt: String? = null,
    val requestedAt: String? = null,
    val outputDir: String? = null,
    val mergedRowCount: Long? = null,
    val errorMessage: String? = null,
    val launchSourceKind: String? = null,
    val executionSnapshotId: String? = null,
    val successfulSourceCount: Int? = null,
    val failedSourceCount: Int? = null,
    val skippedSourceCount: Int? = null,
    val targetStatus: String? = null,
    val targetTableName: String? = null,
    val targetRowsLoaded: Long? = null,
)

@Serializable
data class ModuleRunSourceResultResponse(
    val runSourceResultId: String? = null,
    val sourceName: String,
    val sortOrder: Int,
    val status: String,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val exportedRowCount: Long? = null,
    val mergedRowCount: Long? = null,
    val errorMessage: String? = null,
)

@Serializable
data class ModuleRunEventResponse(
    val runEventId: String? = null,
    val seqNo: Int,
    val timestamp: String? = null,
    val stage: String? = null,
    val eventType: String,
    val severity: String,
    val sourceName: String? = null,
    val message: String? = null,
)

@Serializable
data class ModuleRunArtifactResponse(
    val runArtifactId: String? = null,
    val artifactKind: String,
    val artifactKey: String,
    val filePath: String,
    val storageStatus: String,
    val fileSizeBytes: Long? = null,
    val contentHash: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class ModuleRunDetailsResponse(
    val run: ModuleRunSummaryResponse,
    val summaryJson: String? = null,
    val sourceResults: List<ModuleRunSourceResultResponse> = emptyList(),
    val events: List<ModuleRunEventResponse> = emptyList(),
    val artifacts: List<ModuleRunArtifactResponse> = emptyList(),
)

data class CompactProgressEntry(
    val timestamp: String? = null,
    val message: String,
    val severity: String,
)

data class ModuleRunsRouteState(
    val storage: String,
    val moduleId: String,
)

data class ModuleRunsPageState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val session: ModuleRunPageSessionResponse? = null,
    val history: ModuleRunHistoryResponse? = null,
    val selectedRunId: String? = null,
    val selectedRunDetails: ModuleRunDetailsResponse? = null,
    val historyLimit: Int = 20,
    val historyFilter: ModuleRunsHistoryFilter = ModuleRunsHistoryFilter.ALL,
    val searchQuery: String = "",
)
