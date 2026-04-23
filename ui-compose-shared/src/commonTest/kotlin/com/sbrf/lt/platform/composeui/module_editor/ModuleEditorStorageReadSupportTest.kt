package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModuleEditorStorageReadSupportTest {
    private val selectionSupport = ModuleEditorStoreCatalogSelectionSupport()

    @Test
    fun `files route loads files catalog snapshot through storage boundary`() {
        val expectedForm = sampleModuleEditorConfigFormState()
        val support = ModuleEditorStorageReadSupport(
            api = StubModuleEditorApi(
                loadFilesCatalogHandler = {
                    sampleModuleEditorFilesCatalog(listOf("module-a", "module-b"))
                },
                loadFilesSessionHandler = { moduleId ->
                    sampleModuleEditorSession(moduleId = moduleId, storageMode = "files")
                },
                parseConfigFormHandler = { expectedForm },
            ),
            selectionSupport = selectionSupport,
            configFormSnapshotStore = ModuleEditorStoreConfigFormSnapshotSupport(
                StubModuleEditorApi(parseConfigFormHandler = { expectedForm }),
            ),
        )

        val snapshot = runModuleEditorSuspend {
            support.loadCatalogSnapshot(ModuleEditorRouteState(storage = "files", moduleId = "module-b"))
        }

        assertNotNull(snapshot.filesCatalog)
        assertNull(snapshot.databaseCatalog)
        assertEquals("module-b", snapshot.selectedModuleId)
        assertEquals("files", snapshot.session?.storageMode)
        assertEquals(expectedForm, snapshot.configForm?.state)
    }

    @Test
    fun `database route loads session snapshot through storage boundary`() {
        val expectedForm = sampleModuleEditorConfigFormState()
        val support = ModuleEditorStorageReadSupport(
            api = StubModuleEditorApi(
                loadDatabaseSessionHandler = { moduleId ->
                    sampleModuleEditorSession(moduleId = moduleId, storageMode = "database")
                },
                parseConfigFormHandler = { expectedForm },
            ),
            selectionSupport = selectionSupport,
            configFormSnapshotStore = ModuleEditorStoreConfigFormSnapshotSupport(
                StubModuleEditorApi(parseConfigFormHandler = { expectedForm }),
            ),
        )

        val snapshot = runModuleEditorSuspend {
            support.loadSessionSnapshot(
                route = ModuleEditorRouteState(storage = "database", moduleId = "module-a"),
                moduleId = "module-a",
            )
        }

        assertEquals("module-a", snapshot.moduleId)
        assertEquals("database", snapshot.session.storageMode)
        assertEquals(expectedForm, snapshot.configForm.state)
    }
}
