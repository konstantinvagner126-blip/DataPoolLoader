package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStoreRunRequestSupportTest {
    private val support = ModuleEditorStoreRunRequestSupport()

    @Test
    fun `builds files run request from current draft state`() {
        val request = support.buildFilesRunRequest(
            moduleId = "module-a",
            state = ModuleEditorPageState(
                configTextDraft = "app: {}",
                sqlContentsDraft = linkedMapOf(
                    "classpath:sql/main.sql" to "select 1",
                    "classpath:sql/secondary.sql" to "select 2",
                ),
            ),
        )

        assertEquals("module-a", request.moduleId)
        assertEquals("app: {}", request.configText)
        assertEquals(
            linkedMapOf(
                "classpath:sql/main.sql" to "select 1",
                "classpath:sql/secondary.sql" to "select 2",
            ),
            request.sqlFiles,
        )
    }
}
