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
    val configFormSupport = ModuleEditorStoreConfigFormSupport(api)
    return ModuleEditorStoreDependencies(
        loadingStore = loadingSupport,
        draftStore = ModuleEditorDraftStoreSupport(
            draftSupport = ModuleEditorStoreDraftSupport(),
            sqlResourceSupport = ModuleEditorStoreSqlResourceSupport(configFormSupport),
        ),
        workflowStore = ModuleEditorWorkflowStoreSupport(
            actionSupport = ModuleEditorStoreActionSupport(api, loadingSupport),
            configFormSupport = configFormSupport,
        ),
    )
}
