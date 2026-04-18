package com.sbrf.lt.platform.composeui.module_sync

import com.sbrf.lt.platform.composeui.model.RuntimeContext
import com.sbrf.lt.platform.composeui.model.FilesModulesCatalogResponse

interface ModuleSyncApi {
    suspend fun loadRuntimeContext(): RuntimeContext

    suspend fun loadSyncState(): ModuleSyncStateResponse

    suspend fun loadSyncRuns(limit: Int): ModuleSyncRunsResponse

    suspend fun loadSyncRunDetails(syncRunId: String): ModuleSyncRunDetailsResponse

    suspend fun loadFilesModulesCatalog(): FilesModulesCatalogResponse

    suspend fun syncAll(): SyncRunResultResponse

    suspend fun syncOne(moduleCode: String): SyncRunResultResponse

    suspend fun syncSelected(moduleCodes: List<String>): SyncRunResultResponse
}
