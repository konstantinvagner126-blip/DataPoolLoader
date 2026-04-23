package com.sbrf.lt.platform.composeui.module_editor

internal interface ModuleEditorStorageRunStore {
    suspend fun run(
        storage: String,
        moduleId: String,
        current: ModuleEditorPageState,
    )
}

internal class ModuleEditorStorageRunSupport(
    private val api: ModuleEditorApi,
    private val requestSupport: ModuleEditorStoreRunRequestSupport = ModuleEditorStoreRunRequestSupport(),
) : ModuleEditorStorageRunStore {
    override suspend fun run(
        storage: String,
        moduleId: String,
        current: ModuleEditorPageState,
    ) {
        if (storage == "database") {
            api.startDatabaseRun(moduleId)
        } else {
            api.startFilesRun(requestSupport.buildFilesRunRequest(moduleId, current))
        }
    }
}
