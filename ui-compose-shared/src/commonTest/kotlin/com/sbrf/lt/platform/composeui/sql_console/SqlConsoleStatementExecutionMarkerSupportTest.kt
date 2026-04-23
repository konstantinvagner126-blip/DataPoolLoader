package com.sbrf.lt.platform.composeui.sql_console

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlConsoleStatementExecutionMarkerSupportTest {

    @Test
    fun `pending commit marker applies only to mutating statement`() {
        val execution = sampleExecution(
            transactionState = "PENDING_COMMIT",
            result = SqlConsoleQueryResult(
                sql = "update public.offer set name = 'x'",
                statementType = "COMMAND",
                statementKeyword = "UPDATE",
                shardResults = listOf(successShard("db1", affectedRows = 2)),
                maxRowsPerShard = 200,
            ),
        )

        val markers = buildSqlConsoleStatementExecutionMarkers(execution)

        assertEquals(1, markers.size)
        assertEquals("pending_commit", markers.single().status)
        assertTrue(markers.single().details.any { it.contains("Commit или Rollback") })
    }

    @Test
    fun `failed statement marker includes failing source`() {
        val execution = sampleExecution(
            result = SqlConsoleQueryResult(
                sql = "select * from public.offer",
                statementType = "RESULT_SET",
                statementKeyword = "SELECT",
                shardResults = listOf(
                    successShard("db1", rowCount = 10, durationMillis = 120),
                    failedShard("db2"),
                ),
                maxRowsPerShard = 200,
            ),
        )

        val markers = buildSqlConsoleStatementExecutionMarkers(execution)

        assertEquals("failed", markers.single().status)
        assertTrue(markers.single().details.any { it.contains("Failed source: db2") })
    }

    @Test
    fun `success result set marker includes rows and duration`() {
        val execution = sampleExecution(
            result = SqlConsoleQueryResult(
                sql = "select * from public.offer",
                statementType = "RESULT_SET",
                statementKeyword = "SELECT",
                shardResults = listOf(
                    successShard("db1", rowCount = 10, durationMillis = 120),
                    successShard("db2", rowCount = 15, durationMillis = 340),
                ),
                maxRowsPerShard = 200,
            ),
        )

        val marker = buildSqlConsoleStatementExecutionMarkers(execution).single()

        assertEquals("success", marker.status)
        assertTrue(marker.details.any { it.contains("Rows: 25") })
        assertTrue(marker.details.any { it.contains("Max duration: 340 ms") })
    }

    private fun sampleExecution(
        status: String = "SUCCESS",
        transactionState: String = "NONE",
        result: SqlConsoleQueryResult,
    ): SqlConsoleExecutionResponse =
        SqlConsoleExecutionResponse(
            id = "exec-1",
            status = status,
            startedAt = "2026-04-23T10:00:00Z",
            cancelRequested = false,
            transactionState = transactionState,
            result = result,
        )

    private fun successShard(
        name: String,
        rowCount: Int = 0,
        affectedRows: Int? = null,
        durationMillis: Long? = null,
    ): SqlConsoleShardResult =
        SqlConsoleShardResult(
            shardName = name,
            status = "SUCCESS",
            rowCount = rowCount,
            affectedRows = affectedRows,
            durationMillis = durationMillis,
        )

    private fun failedShard(name: String): SqlConsoleShardResult =
        SqlConsoleShardResult(
            shardName = name,
            status = "FAILED",
            errorMessage = "boom",
        )
}
