package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStoreSaveActionSupportTest {

    @Test
    fun `saveModule delegates to refresh store with storage save response message`() {
        var capturedRoute: String? = null
        var capturedModuleId: String? = null
        var capturedRequest: SaveModuleRequestDto? = null
        var refreshMessage: String? = null
        val support = ModuleEditorStoreSaveActionSupport(
            saveStore = object : ModuleEditorStorageSaveStore {
                override suspend fun save(
                    route: ModuleEditorRouteState,
                    moduleId: String,
                    request: SaveModuleRequestDto,
                ): SaveResultResponseDto {
                    capturedRoute = route.storage
                    capturedModuleId = moduleId
                    capturedRequest = request
                    return SaveResultResponseDto(message = "saved")
                }

                override suspend fun discardWorkingCopy(moduleId: String): SaveResultResponseDto =
                    error("not used")

                override suspend fun publishWorkingCopy(moduleId: String): SaveResultResponseDto =
                    error("not used")
            },
            refreshStore = StubModuleEditorSelectedModuleRefreshStore(
                handler = { current, _, successMessage ->
                    refreshMessage = successMessage
                    current.copy(
                        actionInProgress = null,
                        successMessage = successMessage,
                        errorMessage = null,
                    )
                },
            ),
        )

        val state = runModuleEditorSuspend {
            support.saveModule(
                current = ModuleEditorPageState(
                    selectedModuleId = "module-a",
                    actionInProgress = "saveFilesModule",
                    configTextDraft = "app: {}",
                    metadataDraft = ModuleMetadataDraft(title = "Demo"),
                ),
                route = ModuleEditorRouteState(storage = "files", moduleId = "module-a"),
            )
        }

        assertEquals("files", capturedRoute)
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
            saveStore = object : ModuleEditorStorageSaveStore {
                override suspend fun save(
                    route: ModuleEditorRouteState,
                    moduleId: String,
                    request: SaveModuleRequestDto,
                ): SaveResultResponseDto = error("not used")

                override suspend fun discardWorkingCopy(moduleId: String): SaveResultResponseDto =
                    error("not used")

                override suspend fun publishWorkingCopy(moduleId: String): SaveResultResponseDto {
                    throw IllegalStateException()
                }
            },
            refreshStore = StubModuleEditorSelectedModuleRefreshStore(
                handler = { current, _, successMessage ->
                    current.copy(successMessage = successMessage)
                },
            ),
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
