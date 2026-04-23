package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorWorkingCopyLifecycleSupportTest {

    @Test
    fun `discard working copy delegates to api`() {
        var capturedModuleId: String? = null
        val support = ModuleEditorWorkingCopyLifecycleSupport(
            api = StubModuleEditorApi(
                discardDatabaseWorkingCopyHandler = { moduleId ->
                    capturedModuleId = moduleId
                    SaveResultResponseDto("discarded")
                },
            ),
        )

        val response = runModuleEditorSuspend {
            support.discardWorkingCopy("db:demo")
        }

        assertEquals("db:demo", capturedModuleId)
        assertEquals("discarded", response.message)
    }

    @Test
    fun `publish working copy delegates to api`() {
        var capturedModuleId: String? = null
        val support = ModuleEditorWorkingCopyLifecycleSupport(
            api = StubModuleEditorApi(
                publishDatabaseWorkingCopyHandler = { moduleId ->
                    capturedModuleId = moduleId
                    SaveResultResponseDto("published")
                },
            ),
        )

        val response = runModuleEditorSuspend {
            support.publishWorkingCopy("db:demo")
        }

        assertEquals("db:demo", capturedModuleId)
        assertEquals("published", response.message)
    }
}
