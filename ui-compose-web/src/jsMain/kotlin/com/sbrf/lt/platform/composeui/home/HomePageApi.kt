package com.sbrf.lt.platform.composeui.home

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.FilesModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import com.sbrf.lt.platform.composeui.model.RuntimeModeUpdateRequest
import com.sbrf.lt.platform.composeui.model.RuntimeModeUpdateResponse

interface HomePageApi {
    suspend fun loadHomePageData(): HomePageData

    suspend fun updateRuntimeMode(mode: ModuleStoreMode): RuntimeModeUpdateResponse
}

class HomePageApiClient(
    private val httpClient: ComposeHttpClient = ComposeHttpClient(),
) : HomePageApi {
    override suspend fun loadHomePageData(): HomePageData {
        val runtimeContext = httpClient.get("/api/ui/runtime-context", RuntimeContext.serializer())
        val filesCatalog = httpClient.get("/api/modules/catalog", FilesModulesCatalogResponse.serializer())
        val databaseCatalog = httpClient.getOrNull("/api/db/modules/catalog", DatabaseModulesCatalogResponse.serializer())
        return HomePageData(
            runtimeContext = runtimeContext,
            filesCatalog = filesCatalog,
            databaseCatalog = databaseCatalog,
        )
    }

    override suspend fun updateRuntimeMode(mode: ModuleStoreMode): RuntimeModeUpdateResponse {
        return httpClient.postJson(
            path = "/api/ui/runtime-mode",
            payload = RuntimeModeUpdateRequest(mode),
            serializer = RuntimeModeUpdateRequest.serializer(),
            deserializer = RuntimeModeUpdateResponse.serializer(),
        )
    }
}
