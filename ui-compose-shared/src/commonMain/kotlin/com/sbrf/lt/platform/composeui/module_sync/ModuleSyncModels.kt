package com.sbrf.lt.platform.composeui.module_sync

import com.sbrf.lt.platform.composeui.model.RuntimeContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ActiveModuleSyncRunResponse(
    val syncRunId: String,
    val scope: String,
    val startedAt: String,
    val moduleCode: String? = null,
    val startedByActorId: String? = null,
    val startedByActorSource: String? = null,
    val startedByActorDisplayName: String? = null,
)

@Serializable
data class ModuleSyncStateResponse(
    val maintenanceMode: Boolean = false,
    val activeFullSync: ActiveModuleSyncRunResponse? = null,
    val activeSingleSyncs: List<ActiveModuleSyncRunResponse> = emptyList(),
    val message: String = "",
)

@Serializable
data class ModuleSyncRunSummaryResponse(
    val syncRunId: String,
    val scope: String,
    val moduleCode: String? = null,
    val status: String,
    val startedAt: String,
    val finishedAt: String? = null,
    val startedByActorId: String? = null,
    val startedByActorSource: String? = null,
    val startedByActorDisplayName: String? = null,
    val totalProcessed: Int = 0,
    val totalCreated: Int = 0,
    val totalUpdated: Int = 0,
    val totalSkipped: Int = 0,
    val totalFailed: Int = 0,
)

@Serializable
data class ModuleSyncRunsResponse(
    val runs: List<ModuleSyncRunSummaryResponse> = emptyList(),
)

@Serializable
data class ModuleSyncItemResultResponse(
    val moduleCode: String,
    val action: String,
    val status: String,
    val detectedHash: String,
    val resultRevisionId: String? = null,
    val errorMessage: String? = null,
    val details: JsonObject? = null,
)

@Serializable
data class ModuleSyncRunDetailsResponse(
    val run: ModuleSyncRunSummaryResponse,
    val items: List<ModuleSyncItemResultResponse> = emptyList(),
)

@Serializable
data class SyncOneModuleRequestDto(
    val moduleCode: String,
)

@Serializable
data class SyncRunResultResponse(
    val syncRunId: String,
    val scope: String,
    val moduleCode: String? = null,
    val status: String,
    val startedAt: String,
    val finishedAt: String? = null,
    val totalProcessed: Int = 0,
    val totalCreated: Int = 0,
    val totalUpdated: Int = 0,
    val totalSkipped: Int = 0,
    val totalFailed: Int = 0,
    val errorMessage: String? = null,
)

data class ModuleSyncPageState(
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val actionInProgress: String? = null,
    val runtimeContext: RuntimeContext? = null,
    val syncState: ModuleSyncStateResponse? = null,
    val runs: List<ModuleSyncRunSummaryResponse> = emptyList(),
    val selectedRunId: String? = null,
    val selectedRunDetails: ModuleSyncRunDetailsResponse? = null,
    val historyLimit: Int = 20,
    val syncOneModuleCode: String = "",
    val syncOneInputVisible: Boolean = false,
)
