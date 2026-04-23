package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStoreDatabaseLifecycleStateSupportTest {
    private val support = ModuleEditorStoreDatabaseLifecycleStateSupport()

    @Test
    fun `next route after create keeps hidden module visible and selects created module`() {
        val nextRoute = support.nextRouteAfterCreate(
            route = ModuleEditorRouteState(storage = "database", moduleId = null, includeHidden = false),
            draft = CreateModuleDraft(hiddenFromUi = true),
            response = CreateDbModuleResponseDto(
                message = "created",
                moduleId = "db:demo",
                moduleCode = "demo",
            ),
        )

        assertEquals("demo", nextRoute.moduleId)
        assertEquals(true, nextRoute.includeHidden)
    }

    @Test
    fun `apply created module state resets create dialog and sets settings tab`() {
        val state = support.applyCreatedModuleLoadedState(
            loaded = ModuleEditorPageState(
                loading = true,
                actionInProgress = "createDatabaseModule",
                errorMessage = "old",
                activeTab = ModuleEditorTab.SQL,
                createModuleDialogOpen = true,
                createModuleDraft = CreateModuleDraft(moduleCode = "demo"),
            ),
            response = CreateDbModuleResponseDto(
                message = "Module created",
                moduleId = "db:demo",
                moduleCode = "demo",
            ),
        )

        assertEquals(false, state.loading)
        assertEquals(null, state.actionInProgress)
        assertEquals(null, state.errorMessage)
        assertEquals("Module created", state.successMessage)
        assertEquals(ModuleEditorTab.SETTINGS, state.activeTab)
        assertEquals(false, state.createModuleDialogOpen)
        assertEquals(CreateModuleDraft(), state.createModuleDraft)
    }

    @Test
    fun `apply deleted module state clears create dialog without changing selected tab contract`() {
        val state = support.applyDeletedModuleLoadedState(
            loaded = ModuleEditorPageState(
                loading = true,
                actionInProgress = "deleteDatabaseModule",
                activeTab = ModuleEditorTab.SQL,
                createModuleDialogOpen = true,
                createModuleDraft = CreateModuleDraft(moduleCode = "demo"),
            ),
            response = DeleteModuleResponseDto(
                message = "Module deleted",
                moduleCode = "demo",
            ),
        )

        assertEquals(false, state.loading)
        assertEquals(null, state.actionInProgress)
        assertEquals("Module deleted", state.successMessage)
        assertEquals(ModuleEditorTab.SQL, state.activeTab)
        assertEquals(false, state.createModuleDialogOpen)
        assertEquals(CreateModuleDraft(), state.createModuleDraft)
    }
}
