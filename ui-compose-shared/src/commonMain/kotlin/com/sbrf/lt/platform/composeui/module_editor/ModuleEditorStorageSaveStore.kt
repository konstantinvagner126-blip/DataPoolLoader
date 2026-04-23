package com.sbrf.lt.platform.composeui.module_editor

internal interface ModuleEditorStorageSaveStore {
    suspend fun save(
        route: ModuleEditorRouteState,
        moduleId: String,
        request: SaveModuleRequestDto,
    ): SaveResultResponseDto
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
}
