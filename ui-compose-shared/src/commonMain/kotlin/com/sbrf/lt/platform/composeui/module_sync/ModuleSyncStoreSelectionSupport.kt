package com.sbrf.lt.platform.composeui.module_sync

internal class ModuleSyncStoreSelectionSupport(
    private val api: ModuleSyncApi,
) {
    suspend fun loadSelectedRunDetails(syncRunId: String?): Result<ModuleSyncRunDetailsResponse?> =
        runCatching { syncRunId?.let { api.loadSyncRunDetails(it) } }

    fun resolveSelectedRunId(
        runs: List<ModuleSyncRunSummaryResponse>,
        syncState: ModuleSyncStateResponse,
        preferredRunId: String?,
    ): String? =
        when {
            preferredRunId != null && runs.any { it.syncRunId == preferredRunId } -> preferredRunId
            syncState.activeFullSync != null && runs.any { it.syncRunId == syncState.activeFullSync.syncRunId } ->
                syncState.activeFullSync.syncRunId
            syncState.activeSingleSyncs.isNotEmpty() && runs.any { it.syncRunId == syncState.activeSingleSyncs.first().syncRunId } ->
                syncState.activeSingleSyncs.first().syncRunId
            else -> runs.firstOrNull()?.syncRunId
        }
}
