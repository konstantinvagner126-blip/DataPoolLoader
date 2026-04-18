package com.sbrf.lt.platform.composeui.module_editor

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

    suspend fun createDatabaseModule(request: CreateDbModuleRequestDto): CreateDbModuleResponseDto

    suspend fun deleteDatabaseModule(moduleId: String): DeleteModuleResponseDto

    suspend fun startFilesRun(request: StartRunRequestDto): UiRunSnapshotDto

    suspend fun startDatabaseRun(moduleId: String): DatabaseRunStartResponseDto

    suspend fun parseConfigForm(configText: String): ConfigFormStateDto

    suspend fun applyConfigForm(configText: String, formState: ConfigFormStateDto): ConfigFormUpdateResponseDto
}
