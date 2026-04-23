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
    val saveActionSupport = ModuleEditorStoreSaveActionSupport(api, loadingSupport)
    val runActionSupport = ModuleEditorStoreRunActionSupport(api)
    val databaseLifecycleActionSupport = ModuleEditorStoreDatabaseLifecycleActionSupport(api, loadingSupport)
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
                    configFormSupport = configFormSupport,
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
