package com.sbrf.lt.platform.composeui.module_editor

internal data class ModuleEditorStoreDependencies(
    val loadingStore: ModuleEditorLoadingStore,
    val draftStore: ModuleEditorDraftStore,
    val workflowStore: ModuleEditorWorkflowStore,
)

internal fun createModuleEditorStoreDependencies(
    api: ModuleEditorApi,
    syncRoute: (storage: String, moduleId: String?, includeHidden: Boolean) -> Unit,
): ModuleEditorStoreDependencies {
    val loadingSupport = ModuleEditorStoreLoadingSupport(api, syncRoute)
    val saveActionSupport = ModuleEditorStoreSaveActionSupport(
        saveStore = ModuleEditorStorageSaveSupport(api),
        refreshStore = loadingSupport,
    )
    val runActionSupport = ModuleEditorStoreRunActionSupport(
        runStore = ModuleEditorStorageRunSupport(api),
    )
    val databaseLifecycleActionSupport = ModuleEditorStoreDatabaseLifecycleActionSupport(
        lifecycleStore = ModuleEditorDatabaseLifecycleSupport(api),
        loadingStore = loadingSupport,
    )
    val configFormSupport = ModuleEditorStoreConfigFormSupport(api)
    val sqlResourceNamingSupport = ModuleEditorStoreSqlResourceNamingSupport()
    return ModuleEditorStoreDependencies(
        loadingStore = loadingSupport,
        draftStore = ModuleEditorDraftStoreSupport(
            draftSupport = ModuleEditorStoreDraftSupport(
                statusSupport = ModuleEditorStoreDraftStatusSupport(),
                fieldSupport = ModuleEditorStoreDraftFieldSupport(),
                createModuleSupport = ModuleEditorStoreCreateModuleDraftSupport(),
            ),
            sqlResourceSupport = ModuleEditorStoreSqlResourceSupport(
                mutationSupport = ModuleEditorStoreSqlResourceMutationSupport(
                    formSyncStore = configFormSupport,
                    namingSupport = sqlResourceNamingSupport,
                ),
            ),
        ),
        workflowStore = ModuleEditorWorkflowStoreSupport(
            saveActionSupport = saveActionSupport,
            runActionSupport = runActionSupport,
            databaseLifecycleActionSupport = databaseLifecycleActionSupport,
            configFormSupport = configFormSupport,
        ),
    )
}
