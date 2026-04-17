package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.FilesModulesCatalogResponse

interface ModuleEditorApi {
    suspend fun loadFilesCatalog(): FilesModulesCatalogResponse

    suspend fun loadDatabaseCatalog(includeHidden: Boolean): DatabaseModulesCatalogResponse

    suspend fun loadFilesSession(moduleId: String): ModuleEditorSessionResponse

    suspend fun loadDatabaseSession(moduleId: String): ModuleEditorSessionResponse
}

class ModuleEditorApiClient(
    private val httpClient: ComposeHttpClient = ComposeHttpClient(),
) : ModuleEditorApi {
    override suspend fun loadFilesCatalog(): FilesModulesCatalogResponse =
        httpClient.get("/api/modules/catalog", FilesModulesCatalogResponse.serializer())

    override suspend fun loadDatabaseCatalog(includeHidden: Boolean): DatabaseModulesCatalogResponse {
        val suffix = if (includeHidden) "?includeHidden=true" else ""
        return httpClient.get("/api/db/modules/catalog$suffix", DatabaseModulesCatalogResponse.serializer())
    }

    override suspend fun loadFilesSession(moduleId: String): ModuleEditorSessionResponse =
        httpClient.get("/api/modules/$moduleId", ModuleEditorSessionResponse.serializer())

    override suspend fun loadDatabaseSession(moduleId: String): ModuleEditorSessionResponse =
        httpClient.get("/api/db/modules/$moduleId", ModuleEditorSessionResponse.serializer())
}
