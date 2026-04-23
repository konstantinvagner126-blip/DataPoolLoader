package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStoreSaveActionSupportTest {

    @Test
    fun `saveFilesModule delegates to refresh store with API response message`() {
        var capturedModuleId: String? = null
        var capturedRequest: SaveModuleRequestDto? = null
        var refreshMessage: String? = null
        val support = ModuleEditorStoreSaveActionSupport(
            api = StubModuleEditorApi(
                saveFilesModuleHandler = { moduleId, request ->
                    capturedModuleId = moduleId
                    capturedRequest = request
                    SaveResultResponseDto(message = "saved")
                },
            ),
            refreshStore = StubModuleEditorSelectedModuleRefreshStore { current, _, successMessage ->
                refreshMessage = successMessage
                current.copy(
                    actionInProgress = null,
                    successMessage = successMessage,
                    errorMessage = null,
                )
            },
        )

        val state = runModuleEditorSuspend {
            support.saveFilesModule(
                current = ModuleEditorPageState(
                    selectedModuleId = "module-a",
                    actionInProgress = "saveFilesModule",
                    configTextDraft = "app: {}",
                    metadataDraft = ModuleMetadataDraft(title = "Demo"),
                ),
                route = ModuleEditorRouteState(storage = "files", moduleId = "module-a"),
            )
        }

        assertEquals("module-a", capturedModuleId)
        assertEquals("app: {}", capturedRequest?.configText)
        assertEquals("Demo", capturedRequest?.title)
        assertEquals("saved", refreshMessage)
        assertEquals("saved", state.successMessage)
        assertEquals(null, state.errorMessage)
        assertEquals(null, state.actionInProgress)
    }

    @Test
    fun `publishDatabaseWorkingCopy returns fallback error message on failure`() {
        val support = ModuleEditorStoreSaveActionSupport(
            api = StubModuleEditorApi(
                publishDatabaseWorkingCopyHandler = {
                    throw IllegalStateException()
                },
            ),
            refreshStore = StubModuleEditorSelectedModuleRefreshStore { current, _, successMessage ->
                current.copy(successMessage = successMessage)
            },
        )

        val state = runModuleEditorSuspend {
            support.publishDatabaseWorkingCopy(
                current = ModuleEditorPageState(
                    selectedModuleId = "module-a",
                    actionInProgress = "publishDatabaseWorkingCopy",
                ),
                route = ModuleEditorRouteState(storage = "database", moduleId = "module-a"),
            )
        }

        assertEquals("Не удалось опубликовать черновик.", state.errorMessage)
        assertEquals(null, state.actionInProgress)
    }
}
