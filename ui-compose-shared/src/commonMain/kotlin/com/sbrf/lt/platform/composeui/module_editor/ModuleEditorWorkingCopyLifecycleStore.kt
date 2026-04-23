package com.sbrf.lt.platform.composeui.module_editor

internal interface ModuleEditorWorkingCopyLifecycleStore {
    suspend fun discardWorkingCopy(moduleId: String): SaveResultResponseDto

    suspend fun publishWorkingCopy(moduleId: String): SaveResultResponseDto
}

internal class ModuleEditorWorkingCopyLifecycleSupport(
    private val api: ModuleEditorApi,
) : ModuleEditorWorkingCopyLifecycleStore {
    override suspend fun discardWorkingCopy(moduleId: String): SaveResultResponseDto =
        api.discardDatabaseWorkingCopy(moduleId)

    override suspend fun publishWorkingCopy(moduleId: String): SaveResultResponseDto =
        api.publishDatabaseWorkingCopy(moduleId)
}
