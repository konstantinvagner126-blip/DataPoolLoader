package com.sbrf.lt.platform.composeui.module_editor

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleEditorStoreCatalogSelectionSupportTest {
    private val support = ModuleEditorStoreCatalogSelectionSupport()

    @Test
    fun `initial selection keeps preferred id when it exists`() {
        val selected = support.resolveInitialSelectedModuleId("beta", listOf("alpha", "beta", "gamma"))

        assertEquals("beta", selected)
    }

    @Test
    fun `initial selection falls back to first module when preferred id is missing`() {
        val selected = support.resolveInitialSelectedModuleId("missing", listOf("alpha", "beta"))

        assertEquals("alpha", selected)
    }

    @Test
    fun `refresh selection keeps current module when it is still present`() {
        val selected = support.resolveRefreshedSelectedModuleId("beta", listOf("alpha", "beta", "gamma"))

        assertEquals("beta", selected)
    }

    @Test
    fun `refresh selection falls back to first available module when current id disappeared`() {
        val selected = support.resolveRefreshedSelectedModuleId("missing", listOf("alpha", "beta"))

        assertEquals("alpha", selected)
    }
}
