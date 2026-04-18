package com.sbrf.lt.platform.composeui.module_runs

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient

class ModuleRunsApiClient(
    private val httpClient: ComposeHttpClient = ComposeHttpClient(),
) : ModuleRunsApi {
    override suspend fun loadSession(storage: String, moduleId: String): ModuleRunPageSessionResponse =
        httpClient.get("/api/module-runs/$storage/$moduleId", ModuleRunPageSessionResponse.serializer())

    override suspend fun loadHistory(storage: String, moduleId: String, limit: Int): ModuleRunHistoryResponse =
        httpClient.get("/api/module-runs/$storage/$moduleId/runs?limit=$limit", ModuleRunHistoryResponse.serializer())

    override suspend fun loadRunDetails(storage: String, moduleId: String, runId: String): ModuleRunDetailsResponse =
        httpClient.get("/api/module-runs/$storage/$moduleId/runs/$runId", ModuleRunDetailsResponse.serializer())
}
