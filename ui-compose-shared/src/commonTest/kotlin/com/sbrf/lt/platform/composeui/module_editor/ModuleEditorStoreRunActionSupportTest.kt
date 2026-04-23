package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStoreRunActionSupportTest {

    @Test
    fun `runFilesModule delegates to storage run store and clears transient status`() {
        var capturedStorage: String? = null
        var capturedModuleId: String? = null
        val support = ModuleEditorStoreRunActionSupport(
            runStore = object : ModuleEditorStorageRunStore {
                override suspend fun run(
                    storage: String,
                    moduleId: String,
                    current: ModuleEditorPageState,
                ) {
                    capturedStorage = storage
                    capturedModuleId = moduleId
                }
            },
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

        assertEquals("files", capturedStorage)
        assertEquals("module-a", capturedModuleId)
        assertEquals(null, state.actionInProgress)
        assertEquals(null, state.errorMessage)
        assertEquals(null, state.successMessage)
    }

    @Test
    fun `runDatabaseModule uses fallback error message when API throws without message`() {
        val support = ModuleEditorStoreRunActionSupport(
            runStore = object : ModuleEditorStorageRunStore {
                override suspend fun run(
                    storage: String,
                    moduleId: String,
                    current: ModuleEditorPageState,
                ) {
                    throw IllegalStateException()
                }
            },
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
