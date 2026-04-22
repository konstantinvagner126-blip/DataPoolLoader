package com.sbrf.lt.platform.composeui.module_editor

internal class ModuleEditorStoreLoadingSupport(
    private val api: ModuleEditorApi,
    private val syncRoute: (storage: String, moduleId: String?, includeHidden: Boolean) -> Unit,
) : ModuleEditorLoadingStore {
    private val stateFactory = ModuleEditorStoreStateFactory()
    private val fallbackSupport = ModuleEditorStoreFallbackSupport(api)
    private val catalogLoadingSupport = ModuleEditorStoreCatalogLoadingSupport(
        api = api,
        syncRoute = syncRoute,
        stateFactory = stateFactory,
        fallbackSupport = fallbackSupport,
    )
    private val sessionLoadingSupport = ModuleEditorStoreSessionLoadingSupport(
        api = api,
        syncRoute = syncRoute,
        stateFactory = stateFactory,
        fallbackSupport = fallbackSupport,
    )

    override suspend fun load(route: ModuleEditorRouteState): ModuleEditorPageState =
        catalogLoadingSupport.load(route, ::loadConfigFormSnapshot)

    override suspend fun selectModule(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
        moduleId: String,
    ): ModuleEditorPageState =
        sessionLoadingSupport.selectModule(current, route, moduleId, ::loadConfigFormSnapshot)

    override suspend fun refreshCatalog(
        current: ModuleEditorPageState,
        route: ModuleEditorRouteState,
    ): ModuleEditorPageState =
        catalogLoadingSupport.refreshCatalog(current, route)

    suspend fun refreshSelectedModule(
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

    suspend fun loadConfigFormSnapshot(configText: String): ConfigFormSnapshot =
        runCatching {
            ConfigFormSnapshot(
                state = api.parseConfigForm(configText),
                errorMessage = null,
            )
        }.getOrElse { error ->
            ConfigFormSnapshot(
                state = null,
                errorMessage = error.message ?: "Не удалось собрать визуальную форму.",
            )
        }
}
