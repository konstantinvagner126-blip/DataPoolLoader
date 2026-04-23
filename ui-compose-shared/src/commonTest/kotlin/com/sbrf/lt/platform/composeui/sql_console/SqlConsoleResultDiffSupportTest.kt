package com.sbrf.lt.platform.composeui.sql_console

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqlConsoleResultDiffSupportTest {

    @Test
    fun `build diff view includes row count value mismatch and source failure`() {
        val diff = buildSqlConsoleResultDiffView(
            result = SqlConsoleQueryResult(
                sql = "select * from public.offer",
                statementType = "RESULT_SET",
                statementKeyword = "SELECT",
                maxRowsPerShard = 200,
                shardResults = listOf(
                    successShard(
                        name = "db1",
                        rowCount = 2,
                        columns = listOf("id", "name"),
                        rows = listOf(
                            mapOf("id" to "1", "name" to "alpha"),
                            mapOf("id" to "2", "name" to "beta"),
                        ),
                    ),
                    successShard(
                        name = "db2",
                        rowCount = 3,
                        columns = listOf("id", "name"),
                        rows = listOf(
                            mapOf("id" to "1", "name" to "alpha"),
                            mapOf("id" to "2", "name" to "BETA"),
                            mapOf("id" to "3", "name" to "gamma"),
                        ),
                    ),
                    failedShard("db3"),
                ),
            ),
        )

        assertNotNull(diff)
        assertEquals("db1", diff.baselineSourceName)
        assertEquals(4, diff.totalMismatchCount)
        assertFalse(diff.mismatchLimitReached)
        assertEquals(listOf("BASELINE", "MISMATCH", "FAILED"), diff.sourceSummaries.map { it.state })
        assertTrue(diff.entries.any { it.kind == "ROW_COUNT" && it.sourceName == "db2" })
        assertTrue(diff.entries.any { it.kind == "VALUE_MISMATCH" && it.sourceName == "db2" && it.columnName == "name" && it.rowNumber == 2 })
        assertTrue(diff.entries.any { it.kind == "EXTRA_ROW" && it.sourceName == "db2" && it.rowNumber == 3 })
        assertTrue(diff.entries.any { it.kind == "SOURCE_FAILURE" && it.sourceName == "db3" })
    }

    @Test
    fun `build diff view reports mismatch limit reached`() {
        val diff = buildSqlConsoleResultDiffView(
            result = SqlConsoleQueryResult(
                sql = "select * from public.offer",
                statementType = "RESULT_SET",
                statementKeyword = "SELECT",
                maxRowsPerShard = 200,
                shardResults = listOf(
                    successShard(
                        name = "db1",
                        rowCount = 1,
                        columns = listOf("id", "name"),
                        rows = listOf(mapOf("id" to "1", "name" to "alpha")),
                    ),
                    successShard(
                        name = "db2",
                        rowCount = 1,
                        columns = listOf("id", "name"),
                        rows = listOf(mapOf("id" to "9", "name" to "omega")),
                    ),
                ),
            ),
            mismatchLimit = 1,
        )

        assertNotNull(diff)
        assertEquals(2, diff.totalMismatchCount)
        assertEquals(1, diff.entries.size)
        assertTrue(diff.mismatchLimitReached)
    }

    private fun successShard(
        name: String,
        rowCount: Int,
        columns: List<String>,
        rows: List<Map<String, String?>>,
    ): SqlConsoleShardResult =
        SqlConsoleShardResult(
            shardName = name,
            status = "SUCCESS",
            rowCount = rowCount,
            columns = columns,
            rows = rows,
        )

    private fun failedShard(name: String): SqlConsoleShardResult =
        SqlConsoleShardResult(
            shardName = name,
            status = "FAILED",
            errorMessage = "boom",
        )
}
