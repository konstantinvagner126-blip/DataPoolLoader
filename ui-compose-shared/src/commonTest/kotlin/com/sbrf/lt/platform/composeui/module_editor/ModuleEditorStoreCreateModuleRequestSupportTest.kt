package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModuleEditorStoreCreateModuleRequestSupportTest {
    private val support = ModuleEditorStoreCreateModuleRequestSupport()

    @Test
    fun `validates required create-module fields`() {
        assertEquals(
            "Укажи код модуля.",
            support.validateDraft(CreateModuleDraft(moduleCode = "", title = "Title", configText = "x")),
        )
        assertEquals(
            "Укажи название модуля.",
            support.validateDraft(CreateModuleDraft(moduleCode = "demo", title = "", configText = "x")),
        )
        assertEquals(
            "Стартовый application.yml не должен быть пустым.",
            support.validateDraft(CreateModuleDraft(moduleCode = "demo", title = "Title", configText = "")),
        )
        assertNull(
            support.validateDraft(CreateModuleDraft(moduleCode = "demo", title = "Title", configText = "app: {}")),
        )
    }

    @Test
    fun `builds create-module request with trimmed fields and distinct tags`() {
        val request = support.buildRequest(
            CreateModuleDraft(
                moduleCode = "  demo-code  ",
                title = "  Demo title  ",
                description = "   ",
                tagsText = "alpha, beta, alpha , , gamma",
                hiddenFromUi = false,
                configText = "app: {}",
            ),
        )

        assertEquals("demo-code", request.moduleCode)
        assertEquals("Demo title", request.title)
        assertEquals(null, request.description)
        assertEquals(listOf("alpha", "beta", "gamma"), request.tags)
        assertEquals(false, request.hiddenFromUi)
        assertEquals("app: {}", request.configText)
    }
}
