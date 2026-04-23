package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStoreSaveRequestSupportTest {
    private val support = ModuleEditorStoreSaveRequestSupport()

    @Test
    fun `builds save request from page draft state`() {
        val request = support.buildSaveRequest(
            ModuleEditorPageState(
                configTextDraft = "app: {}",
                sqlContentsDraft = linkedMapOf("classpath:sql/main.sql" to "select 1"),
                metadataDraft = ModuleMetadataDraft(
                    title = "Demo module",
                    description = "  ",
                    tags = listOf("alpha", "beta"),
                    hiddenFromUi = true,
                ),
            ),
        )

        assertEquals("app: {}", request.configText)
        assertEquals(linkedMapOf("classpath:sql/main.sql" to "select 1"), request.sqlFiles)
        assertEquals("Demo module", request.title)
        assertEquals(null, request.description)
        assertEquals(listOf("alpha", "beta"), request.tags)
        assertEquals(true, request.hiddenFromUi)
    }
}
