package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreLoadingSupport(
    private val api: ModuleEditorApi,
    private val syncRoute: (storage: String, moduleId: String?, includeHidden: Boolean) -> Unit,
) : ModuleEditorLoadingStore, ModuleEditorSelectedModuleRefreshStore {
    private val stateFactory = ModuleEditorStoreStateFactory()
    private val fallbackSupport = ModuleEditorStoreFallbackSupport(api)
    private val selectionSupport = ModuleEditorStoreCatalogSelectionSupport()
    private val configFormSnapshotStore = ModuleEditorStoreConfigFormSnapshotSupport(api)
    private val storageReadSupport = ModuleEditorStorageReadSupport(
        api = api,
        selectionSupport = selectionSupport,
        configFormSnapshotStore = configFormSnapshotStore,
    )
    private val catalogLoadingSupport = ModuleEditorStoreCatalogLoadingSupport(
        storageReadSupport = storageReadSupport,
        syncRoute = syncRoute,
        stateFactory = stateFactory,
        fallbackSupport = fallbackSupport,
    )
    private val sessionLoadingSupport = ModuleEditorStoreSessionLoadingSupport(
        storageReadSupport = storageReadSupport,
        syncRoute = syncRoute,
        stateFactory = stateFactory,
        fallbackSupport = fallbackSupport,
    )

    override suspend fun load(route: ModuleEditorRouteState): ModuleEditorPageState =
        catalogLoadingSupport.load(route)

    override suspend fun selectModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
        moduleId: String,
    ): ModuleEditorPageState =
        sessionLoadingSupport.selectModule(current, route, moduleId)

    override suspend fun refreshCatalog(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        catalogLoadingSupport.refreshCatalog(current, route)

    override suspend fun refreshSelectedModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
        successMessage: String,
    ): ModuleEditorPageState =
        sessionLoadingSupport.refreshSelectedModule(
            current,
            route,
            successMessage,
            ::selectModule,
        )
}
