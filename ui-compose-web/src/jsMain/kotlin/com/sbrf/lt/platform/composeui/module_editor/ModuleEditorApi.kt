package com.sbrf.lt.platform.composeui.module_editor

import com.sbrf.lt.platform.composeui.foundation.http.ComposeHttpClient
import com.sbrf.lt.platform.composeui.model.DatabaseModulesCatalogResponse
import com.sbrf.lt.platform.composeui.model.FilesModulesCatalogResponse

interface ModuleEditorApi {
    suspend fun loadFilesCatalog(): FilesModulesCatalogResponse

    suspend fun loadDatabaseCatalog(includeHidden: Boolean): DatabaseModulesCatalogResponse

    suspend fun loadFilesSession(moduleId: String): ModuleEditorSessionResponse

    suspend fun loadDatabaseSession(moduleId: String): ModuleEditorSessionResponse

    suspend fun saveFilesModule(moduleId: String, request: SaveModuleRequestDto): SaveResultResponseDto

    suspend fun saveDatabaseWorkingCopy(moduleId: String, request: SaveModuleRequestDto): SaveResultResponseDto

    suspend fun discardDatabaseWorkingCopy(moduleId: String): SaveResultResponseDto

    suspend fun publishDatabaseWorkingCopy(moduleId: String): SaveResultResponseDto

    suspend fun startFilesRun(request: StartRunRequestDto): UiRunSnapshotDto

    suspend fun startDatabaseRun(moduleId: String): DatabaseRunStartResponseDto

    suspend fun parseConfigForm(configText: String): ConfigFormStateDto

    suspend fun applyConfigForm(configText: String, formState: ConfigFormStateDto): ConfigFormUpdateResponseDto
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

    override suspend fun saveFilesModule(moduleId: String, request: SaveModuleRequestDto): SaveResultResponseDto =
        httpClient.postJson(
            "/api/modules/$moduleId/save",
            request,
            SaveModuleRequestDto.serializer(),
            SaveResultResponseDto.serializer(),
        )

    override suspend fun saveDatabaseWorkingCopy(moduleId: String, request: SaveModuleRequestDto): SaveResultResponseDto =
        httpClient.postJson(
            "/api/db/modules/$moduleId/save",
            request,
            SaveModuleRequestDto.serializer(),
            SaveResultResponseDto.serializer(),
        )

    override suspend fun discardDatabaseWorkingCopy(moduleId: String): SaveResultResponseDto =
        httpClient.postJson(
            "/api/db/modules/$moduleId/discard-working-copy",
            EmptyRequestDto(),
            EmptyRequestDto.serializer(),
            SaveResultResponseDto.serializer(),
        )

    override suspend fun publishDatabaseWorkingCopy(moduleId: String): SaveResultResponseDto =
        httpClient.postJson(
            "/api/db/modules/$moduleId/publish",
            EmptyRequestDto(),
            EmptyRequestDto.serializer(),
            SaveResultResponseDto.serializer(),
        )

    override suspend fun startFilesRun(request: StartRunRequestDto): UiRunSnapshotDto =
        httpClient.postJson(
            "/api/runs",
            request,
            StartRunRequestDto.serializer(),
            UiRunSnapshotDto.serializer(),
        )

    override suspend fun startDatabaseRun(moduleId: String): DatabaseRunStartResponseDto =
        httpClient.postJson(
            "/api/db/modules/$moduleId/run",
            DatabaseRunStartRequestDto(),
            DatabaseRunStartRequestDto.serializer(),
            DatabaseRunStartResponseDto.serializer(),
        )

    override suspend fun parseConfigForm(configText: String): ConfigFormStateDto =
        httpClient.postJson(
            "/api/config-form/parse",
            ConfigFormParseRequestDto(configText),
            ConfigFormParseRequestDto.serializer(),
            ConfigFormStateDto.serializer(),
        )

    override suspend fun applyConfigForm(
        configText: String,
        formState: ConfigFormStateDto,
    ): ConfigFormUpdateResponseDto =
        httpClient.postJson(
            "/api/config-form/update",
            ConfigFormUpdateRequestDto(configText = configText, formState = formState),
            ConfigFormUpdateRequestDto.serializer(),
            ConfigFormUpdateResponseDto.serializer(),
        )
}
