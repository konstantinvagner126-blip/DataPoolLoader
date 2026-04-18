package com.sbrf.lt.platform.composeui.module_sync

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.model.RuntimeContext

class ModuleSyncApiClient(
    private val httpClient: ComposeHttpClient = ComposeHttpClient(),
) : ModuleSyncApi {
    override suspend fun loadRuntimeContext(): RuntimeContext =
        httpClient.get("/api/ui/runtime-context", RuntimeContext.serializer())

    override suspend fun loadSyncState(): ModuleSyncStateResponse =
        httpClient.get("/api/db/sync/state", ModuleSyncStateResponse.serializer())

    override suspend fun loadSyncRuns(limit: Int): ModuleSyncRunsResponse =
        httpClient.get("/api/db/sync/runs?limit=$limit", ModuleSyncRunsResponse.serializer())

    override suspend fun loadSyncRunDetails(syncRunId: String): ModuleSyncRunDetailsResponse =
        httpClient.get("/api/db/sync/runs/$syncRunId", ModuleSyncRunDetailsResponse.serializer())

    override suspend fun syncAll(): SyncRunResultResponse =
        httpClient.postJson(
            path = "/api/db/sync/all",
            payload = EmptySyncRequestDto(),
            serializer = EmptySyncRequestDto.serializer(),
            deserializer = SyncRunResultResponse.serializer(),
        )

    override suspend fun syncOne(moduleCode: String): SyncRunResultResponse =
        httpClient.postJson(
            path = "/api/db/sync/one",
            payload = SyncOneModuleRequestDto(moduleCode),
            serializer = SyncOneModuleRequestDto.serializer(),
            deserializer = SyncRunResultResponse.serializer(),
        )
}

@kotlinx.serialization.Serializable
private data class EmptySyncRequestDto(
    val placeholder: String? = null,
)
