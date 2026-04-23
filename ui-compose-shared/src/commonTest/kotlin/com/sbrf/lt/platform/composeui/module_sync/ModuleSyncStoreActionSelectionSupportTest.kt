package com.sbrf.lt.platform.composeui.module_sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModuleSyncStoreActionSelectionSupportTest {
    private val support = ModuleSyncStoreActionSelectionSupport()

    @Test
    fun `resolveSingleModuleCode returns trimmed value only for single selection`() {
        assertEquals("alpha", support.resolveSingleModuleCode(setOf(" alpha ")))
        assertNull(support.resolveSingleModuleCode(emptySet()))
        assertNull(support.resolveSingleModuleCode(setOf("alpha", "beta")))
    }

    @Test
    fun `resolveSelectedModuleCodes trims filters blanks and deduplicates`() {
        assertEquals(
            listOf("alpha", "beta"),
            support.resolveSelectedModuleCodes(linkedSetOf(" alpha ", "", "beta", "alpha")),
        )
    }
}
