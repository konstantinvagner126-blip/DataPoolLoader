package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStoreDatabaseLifecycleActionSupportTest {

    @Test
    fun `createDatabaseModule delegates to lifecycle store and reloads created route`() {
        var capturedRequest: CreateDbModuleRequestDto? = null
        var loadedRoute: ModuleEditorRouteState? = null
        val support = ModuleEditorStoreDatabaseLifecycleActionSupport(
            lifecycleStore = object : ModuleEditorDatabaseLifecycleStore {
                override suspend fun createModule(request: CreateDbModuleRequestDto): CreateDbModuleResponseDto {
                    capturedRequest = request
                    return CreateDbModuleResponseDto(
                        message = "created",
                        moduleId = "db:demo",
                        moduleCode = "demo",
                    )
                }

                override suspend fun deleteModule(moduleId: String): DeleteModuleResponseDto =
                    error("not used")
            },
            loadingStore = object : ModuleEditorLoadingStore {
                override suspend fun load(route: ModuleEditorRouteState): ModuleEditorPageState {
                    loadedRoute = route
                    return ModuleEditorPageState(
                        loading = true,
                        actionInProgress = "createDatabaseModule",
                        activeTab = ModuleEditorTab.SQL,
                        createModuleDialogOpen = true,
                        createModuleDraft = CreateModuleDraft(moduleCode = "demo"),
                    )
                }

                override suspend fun selectModule(
                    current: ModuleEditorPageState,
                    route: ModuleEditorRouteState,
                    moduleId: String,
                ): ModuleEditorPageState = error("not used")

                override suspend fun refreshCatalog(
                    current: ModuleEditorPageState,
                    route: ModuleEditorRouteState,
                ): ModuleEditorPageState = error("not used")
            },
        )

        val state = runModuleEditorSuspend {
            support.createDatabaseModule(
                current = ModuleEditorPageState(
                    actionInProgress = "createDatabaseModule",
                    createModuleDialogOpen = true,
                    createModuleDraft = CreateModuleDraft(
                        moduleCode = "demo",
                        title = "Demo",
                        configText = "app: {}",
                        hiddenFromUi = true,
                    ),
                ),
                route = ModuleEditorRouteState(storage = "database", includeHidden = false),
            )
        }

        assertEquals("demo", capturedRequest?.moduleCode)
        assertEquals("Demo", capturedRequest?.title)
        assertEquals(
            ModuleEditorRouteState(storage = "database", moduleId = "demo", includeHidden = true),
            loadedRoute,
        )
        assertEquals("created", state.successMessage)
        assertEquals(null, state.errorMessage)
        assertEquals(false, state.createModuleDialogOpen)
        assertEquals(CreateModuleDraft(), state.createModuleDraft)
        assertEquals(ModuleEditorTab.SETTINGS, state.activeTab)
    }

    @Test
    fun `deleteDatabaseModule delegates to lifecycle store and reloads catalog route`() {
        var deletedModuleId: String? = null
        var loadedRoute: ModuleEditorRouteState? = null
        val support = ModuleEditorStoreDatabaseLifecycleActionSupport(
            lifecycleStore = object : ModuleEditorDatabaseLifecycleStore {
                override suspend fun createModule(request: CreateDbModuleRequestDto): CreateDbModuleResponseDto =
                    error("not used")

                override suspend fun deleteModule(moduleId: String): DeleteModuleResponseDto {
                    deletedModuleId = moduleId
                    return DeleteModuleResponseDto(
                        message = "deleted",
                        moduleCode = "demo",
                    )
                }
            },
            loadingStore = object : ModuleEditorLoadingStore {
                override suspend fun load(route: ModuleEditorRouteState): ModuleEditorPageState {
                    loadedRoute = route
                    return ModuleEditorPageState(
                        loading = true,
                        actionInProgress = "deleteDatabaseModule",
                        activeTab = ModuleEditorTab.SQL,
                    )
                }

                override suspend fun selectModule(
                    current: ModuleEditorPageState,
                    route: ModuleEditorRouteState,
                    moduleId: String,
                ): ModuleEditorPageState = error("not used")

                override suspend fun refreshCatalog(
                    current: ModuleEditorPageState,
                    route: ModuleEditorRouteState,
                ): ModuleEditorPageState = error("not used")
            },
        )

        val state = runModuleEditorSuspend {
            support.deleteDatabaseModule(
                current = ModuleEditorPageState(
                    selectedModuleId = "db:demo",
                    actionInProgress = "deleteDatabaseModule",
                ),
                route = ModuleEditorRouteState(storage = "database", moduleId = "demo", includeHidden = true),
            )
        }

        assertEquals("db:demo", deletedModuleId)
        assertEquals(
            ModuleEditorRouteState(storage = "database", moduleId = null, includeHidden = true),
            loadedRoute,
        )
        assertEquals("deleted", state.successMessage)
        assertEquals(null, state.errorMessage)
        assertEquals(null, state.actionInProgress)
    }
}
