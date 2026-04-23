package com.sbrf.lt.platform.composeui.module_editor

internal interface ModuleEditorStorageSaveStore {
    suspend fun save(
        route: ModuleEditorRouteState,
        moduleId: String,
        request: SaveModuleRequestDto,
    ): SaveResultResponseDto

    suspend fun discardWorkingCopy(moduleId: String): SaveResultResponseDto

    suspend fun publishWorkingCopy(moduleId: String): SaveResultResponseDto
}

internal class ModuleEditorStorageSaveSupport(
    private val api: ModuleEditorApi,
) : ModuleEditorStorageSaveStore {
    override suspend fun save(
        route: ModuleEditorRouteState,
        moduleId: String,
        request: SaveModuleRequestDto,
    ): SaveResultResponseDto =
        if (route.storage == "database") {
            api.saveDatabaseWorkingCopy(moduleId, request)
        } else {
            api.saveFilesModule(moduleId, request)
        }

    override suspend fun discardWorkingCopy(moduleId: String): SaveResultResponseDto =
        api.discardDatabaseWorkingCopy(moduleId)

    override suspend fun publishWorkingCopy(moduleId: String): SaveResultResponseDto =
        api.publishDatabaseWorkingCopy(moduleId)
}
