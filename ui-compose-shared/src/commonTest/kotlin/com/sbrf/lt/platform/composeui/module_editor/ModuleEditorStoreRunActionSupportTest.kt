package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStoreRunActionSupportTest {

    @Test
    fun `runModule delegates to storage run store and clears transient status for files route`() {
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
            support.runModule(
                current = ModuleEditorPageState(
                    selectedModuleId = "module-a",
                    actionInProgress = "run",
                    errorMessage = "old",
                    successMessage = "old",
                    configTextDraft = "app: {}",
                    sqlContentsDraft = linkedMapOf("classpath:sql/main.sql" to "select 1"),
                ),
                route = ModuleEditorRouteState(storage = "files", moduleId = "module-a"),
            )
        }

        assertEquals("files", capturedStorage)
        assertEquals("module-a", capturedModuleId)
        assertEquals(null, state.actionInProgress)
        assertEquals(null, state.errorMessage)
        assertEquals(null, state.successMessage)
    }

    @Test
    fun `runModule uses fallback error message when database route fails without message`() {
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
            support.runModule(
                current = ModuleEditorPageState(
                    selectedModuleId = "module-a",
                    actionInProgress = "run",
                ),
                route = ModuleEditorRouteState(storage = "database", moduleId = "module-a"),
            )
        }

        assertEquals(null, state.actionInProgress)
        assertEquals("Не удалось запустить модуль из базы данных.", state.errorMessage)
    }
}
