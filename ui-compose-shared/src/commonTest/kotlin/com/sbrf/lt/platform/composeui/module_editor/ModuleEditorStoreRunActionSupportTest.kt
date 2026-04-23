package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStoreRunActionSupportTest {

    @Test
    fun `runFilesModule sends current draft request and clears transient status`() {
        var capturedRequest: StartRunRequestDto? = null
        val support = ModuleEditorStoreRunActionSupport(
            api = StubModuleEditorApi(
                startFilesRunHandler = { request ->
                    capturedRequest = request
                    UiRunSnapshotDto(
                        id = "run-1",
                        moduleId = request.moduleId,
                        moduleTitle = "Demo",
                        status = "RUNNING",
                        startedAt = "2026-04-23T10:00:00Z",
                    )
                },
            ),
        )

        val state = runModuleEditorSuspend {
            support.runFilesModule(
                ModuleEditorPageState(
                    selectedModuleId = "module-a",
                    actionInProgress = "runFilesModule",
                    errorMessage = "old",
                    successMessage = "old",
                    configTextDraft = "app: {}",
                    sqlContentsDraft = linkedMapOf("classpath:sql/main.sql" to "select 1"),
                ),
            )
        }

        assertEquals("module-a", capturedRequest?.moduleId)
        assertEquals("app: {}", capturedRequest?.configText)
        assertEquals(null, state.actionInProgress)
        assertEquals(null, state.errorMessage)
        assertEquals(null, state.successMessage)
    }

    @Test
    fun `runDatabaseModule uses fallback error message when API throws without message`() {
        val support = ModuleEditorStoreRunActionSupport(
            api = StubModuleEditorApi(
                startDatabaseRunHandler = {
                    throw IllegalStateException()
                },
            ),
        )

        val state = runModuleEditorSuspend {
            support.runDatabaseModule(
                ModuleEditorPageState(
                    selectedModuleId = "module-a",
                    actionInProgress = "runDatabaseModule",
                ),
            )
        }

        assertEquals(null, state.actionInProgress)
        assertEquals("Не удалось запустить модуль из базы данных.", state.errorMessage)
    }
}
