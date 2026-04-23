package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStorageSaveSupportTest {

    @Test
    fun `files route delegates save to files api`() {
        var savedModuleId: String? = null
        var savedConfigText: String? = null
        val support = ModuleEditorStorageSaveSupport(
            StubModuleEditorApi(
                saveFilesModuleHandler = { moduleId, request ->
                    savedModuleId = moduleId
                    savedConfigText = request.configText
                    SaveResultResponseDto(message = "saved files")
                },
            ),
        )

        val response = runModuleEditorSuspend {
            support.save(
                route = ModuleEditorRouteState(storage = "files", moduleId = "module-a"),
                moduleId = "module-a",
                request = SaveModuleRequestDto(
                    configText = "app: {}",
                    sqlFiles = emptyMap(),
                    title = "Demo",
                ),
            )
        }

        assertEquals("module-a", savedModuleId)
        assertEquals("app: {}", savedConfigText)
        assertEquals("saved files", response.message)
    }

    @Test
    fun `database route delegates save to database working copy api`() {
        var savedModuleId: String? = null
        val support = ModuleEditorStorageSaveSupport(
            StubModuleEditorApi(
                saveDatabaseWorkingCopyHandler = { moduleId, _ ->
                    savedModuleId = moduleId
                    SaveResultResponseDto(message = "saved db")
                },
            ),
        )

        val response = runModuleEditorSuspend {
            support.save(
                route = ModuleEditorRouteState(storage = "database", moduleId = "module-b"),
                moduleId = "module-b",
                request = SaveModuleRequestDto(
                    configText = "app: {}",
                    sqlFiles = emptyMap(),
                    title = "Demo",
                ),
            )
        }

        assertEquals("module-b", savedModuleId)
        assertEquals("saved db", response.message)
    }
}
