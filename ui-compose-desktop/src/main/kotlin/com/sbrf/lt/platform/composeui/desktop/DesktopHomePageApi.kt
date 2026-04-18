package com.sbrf.lt.platform.composeui.desktop

import com.sbrf.lt.platform.composeui.home.HomePageApi
import com.sbrf.lt.platform.composeui.home.HomePageData
import com.sbrf.lt.platform.composeui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.FilesModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import com.sbrf.lt.platform.composeui.model.RuntimeContext
import com.sbrf.lt.platform.composeui.model.RuntimeModeUpdateRequest
import com.sbrf.lt.platform.composeui.model.RuntimeModeUpdateResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopHomePageApi(
    private val httpClient: DesktopHttpJsonClient,
) : HomePageApi {
    override suspend fun loadHomePageData(): HomePageData = withContext(Dispatchers.IO) {
        val runtimeContext = httpClient.get("/api/ui/runtime-context", RuntimeContext.serializer())
        val filesCatalog = httpClient.get("/api/modules/catalog", FilesModulesCatalogResponse.serializer())
        val databaseCatalog = httpClient.getOrNull("/api/db/modules/catalog", DatabaseModulesCatalogResponse.serializer())
        HomePageData(
            runtimeContext = runtimeContext,
            filesCatalog = filesCatalog,
            databaseCatalog = databaseCatalog,
        )
    }

    override suspend fun updateRuntimeMode(mode: ModuleStoreMode): RuntimeModeUpdateResponse = withContext(Dispatchers.IO) {
        httpClient.postJson(
            path = "/api/ui/runtime-mode",
            payload = RuntimeModeUpdateRequest(mode),
            serializer = RuntimeModeUpdateRequest.serializer(),
            responseSerializer = RuntimeModeUpdateResponse.serializer(),
        )
    }
}

data class DesktopRuntimeConfig(
    val serverBaseUrl: String,
) {
    companion object {
        fun load(): DesktopRuntimeConfig {
            val raw = System.getProperty("datapool.uiServerUrl")
                ?: System.getenv("DATA_POOL_UI_SERVER_URL")
                ?: "http://127.0.0.1:8080"
            return DesktopRuntimeConfig(serverBaseUrl = raw.trimEnd('/'))
        }
    }
}
