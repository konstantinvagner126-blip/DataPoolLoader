package com.sbrf.lt.platform.composeui.sql_console

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlConsoleConnectionStatusSupportTest {

    @Test
    fun `marks source available after successful execution`() {
        val statuses = observedExecutionSourceStatuses(
            SqlConsoleQueryResult(
                sql = "select 1",
                statementType = "RESULT_SET",
                statementKeyword = "SELECT",
                shardResults = listOf(
                    SqlConsoleShardResult(
                        shardName = "source-1",
                        status = "SUCCESS",
                        connectionState = "AVAILABLE",
                    ),
                ),
                maxRowsPerShard = 100,
            ),
        )

        assertEquals(1, statuses.size)
        assertEquals("source-1", statuses.single().sourceName)
        assertEquals("OK", statuses.single().status)
    }

    @Test
    fun `keeps source available on SQL execution error`() {
        val statuses = observedExecutionSourceStatuses(
            SqlConsoleQueryResult(
                sql = "select from demo",
                statementType = "COMMAND",
                statementKeyword = "SELECT",
                shardResults = listOf(
                    SqlConsoleShardResult(
                        shardName = "source-1",
                        status = "FAILED",
                        errorMessage = "syntax error",
                        connectionState = "AVAILABLE",
                    ),
                ),
                maxRowsPerShard = 100,
            ),
        )

        assertEquals("OK", statuses.single().status)
        assertTrue(statuses.single().message.orEmpty().contains("syntax error"))
    }

    @Test
    fun `merges only observed source statuses`() {
        val merged = mergeSourceConnectionStatuses(
            current = listOf(
                SqlConsoleSourceConnectionStatus(sourceName = "source-1", status = "OK"),
                SqlConsoleSourceConnectionStatus(sourceName = "source-2", status = "FAILED"),
            ),
            observed = listOf(
                SqlConsoleSourceConnectionStatus(sourceName = "source-2", status = "OK"),
            ),
        )

        assertEquals(
            listOf("source-1" to "OK", "source-2" to "OK"),
            merged.map { it.sourceName to it.status },
        )
    }
}
