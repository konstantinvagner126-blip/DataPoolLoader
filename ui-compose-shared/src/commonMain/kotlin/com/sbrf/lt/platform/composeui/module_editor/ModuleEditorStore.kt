package com.sbrf.lt.platform.composeui.module_editor

class ModuleEditorStore private constructor(
    loadingStore: ModuleEditorLoadingStore,
    draftStore: ModuleEditorDraftStore,
    workflowStore: ModuleEditorWorkflowStore,
) : ModuleEditorLoadingStore by loadingStore,
    ModuleEditorDraftStore by draftStore,
    ModuleEditorWorkflowStore by workflowStore {
    constructor(
        api: ModuleEditorApi,
        syncRoute: (storage: String, moduleId: String?, includeHidden: Boolean) -> Unit = { _, _, _ -> },
    ) : this(createModuleEditorStoreDependencies(api, syncRoute))

    private constructor(
        dependencies: ModuleEditorStoreDependencies,
    ) : this(
        loadingStore = dependencies.loadingStore,
        draftStore = dependencies.draftStore,
        workflowStore = dependencies.workflowStore,
    )
}
