package com.sbrf.lt.platform.composeui.sql_console

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlConsoleAnalysisTest {

    @Test
    fun `plain explain of mutating statement stays read only`() {
        val analysis = analyzeSqlStatement("explain update public.offer set name = 'x'")

        assertEquals("EXPLAIN UPDATE", analysis.keyword)
        assertTrue(analysis.readOnly)
        assertFalse(analysis.dangerous)
    }

    @Test
    fun `explain analyze of mutating statement is not read only`() {
        val analysis = analyzeSqlStatement("explain analyze update public.offer set name = 'x'")

        assertEquals("EXPLAIN ANALYZE UPDATE", analysis.keyword)
        assertFalse(analysis.readOnly)
        assertFalse(analysis.dangerous)
    }

    @Test
    fun `explain analyze of select stays read only`() {
        val analysis = analyzeSqlStatement("explain analyze select * from public.offer")

        assertEquals("EXPLAIN ANALYZE SELECT", analysis.keyword)
        assertTrue(analysis.readOnly)
        assertFalse(analysis.dangerous)
    }
}
