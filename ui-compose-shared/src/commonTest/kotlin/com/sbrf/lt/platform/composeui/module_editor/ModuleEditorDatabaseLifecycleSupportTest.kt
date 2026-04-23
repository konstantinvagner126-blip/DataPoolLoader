package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorDatabaseLifecycleSupportTest {

    @Test
    fun `create module delegates to database lifecycle api`() {
        var capturedRequest: CreateDbModuleRequestDto? = null
        val support = ModuleEditorDatabaseLifecycleSupport(
            api = StubModuleEditorApi(
                createDatabaseModuleHandler = { request ->
                    capturedRequest = request
                    CreateDbModuleResponseDto(
                        message = "created",
                        moduleId = "db:demo",
                        moduleCode = "demo",
                    )
                },
            ),
        )

        val response = runModuleEditorSuspend {
            support.createModule(
                CreateDbModuleRequestDto(
                    moduleCode = "demo",
                    title = "Demo",
                    configText = "app: {}",
                    hiddenFromUi = true,
                ),
            )
        }

        assertEquals("demo", capturedRequest?.moduleCode)
        assertEquals("Demo", capturedRequest?.title)
        assertEquals("created", response.message)
    }

    @Test
    fun `delete module delegates to database lifecycle api`() {
        var capturedModuleId: String? = null
        val support = ModuleEditorDatabaseLifecycleSupport(
            api = StubModuleEditorApi(
                deleteDatabaseModuleHandler = { moduleId ->
                    capturedModuleId = moduleId
                    DeleteModuleResponseDto(
                        message = "deleted",
                        moduleCode = "demo",
                    )
                },
            ),
        )

        val response = runModuleEditorSuspend {
            support.deleteModule("db:demo")
        }

        assertEquals("db:demo", capturedModuleId)
        assertEquals("deleted", response.message)
    }
}
