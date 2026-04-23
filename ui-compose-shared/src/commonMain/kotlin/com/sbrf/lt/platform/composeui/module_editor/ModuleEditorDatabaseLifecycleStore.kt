package com.sbrf.lt.platform.composeui.module_editor

internal interface ModuleEditorDatabaseLifecycleStore {
    suspend fun createModule(request: CreateDbModuleRequestDto): CreateDbModuleResponseDto

    suspend fun deleteModule(moduleId: String): DeleteModuleResponseDto
}

internal class ModuleEditorDatabaseLifecycleSupport(
    private val api: ModuleEditorApi,
) : ModuleEditorDatabaseLifecycleStore {
    override suspend fun createModule(request: CreateDbModuleRequestDto): CreateDbModuleResponseDto =
        api.createDatabaseModule(request)

    override suspend fun deleteModule(moduleId: String): DeleteModuleResponseDto =
        api.deleteDatabaseModule(moduleId)
}
