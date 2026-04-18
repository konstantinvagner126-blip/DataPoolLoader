package com.sbrf.lt.platform.ui.model

import com.sbrf.lt.datapool.sqlconsole.RawShardExecutionResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleInfo
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleQueryResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleStatementType
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExecutionSnapshot
import com.sbrf.lt.platform.ui.sqlconsole.SqlConsoleExecutionStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class UiModelsTest {

    @Test
    fun `maps sql console info to response`() {
        val response = SqlConsoleInfo(
            configured = true,
            sourceNames = listOf("db1", "db2"),
            maxRowsPerShard = 200,
            queryTimeoutSec = 60,
        ).toResponse()

        assertEquals(true, response.configured)
        assertEquals(listOf("db1", "db2"), response.sourceNames)
        assertEquals(200, response.maxRowsPerShard)
        assertEquals(60, response.queryTimeoutSec)
    }

    @Test
    fun `maps sql console query result to response`() {
        val response = SqlConsoleQueryResult(
            sql = "select 1",
            statementType = SqlConsoleStatementType.RESULT_SET,
            statementKeyword = "SELECT",
            maxRowsPerShard = 50,
            shardResults = listOf(
                RawShardExecutionResult(
                    shardName = "db1",
                    status = "SUCCESS",
                    columns = listOf("id"),
                    rows = listOf(mapOf("id" to "1")),
                    truncated = true,
                    message = "ok",
                ),
            ),
        ).toResponse()

        assertEquals("RESULT_SET", response.statementType)
        assertEquals("SELECT", response.statementKeyword)
        assertEquals(50, response.maxRowsPerShard)
        assertEquals("db1", response.shardResults.single().shardName)
        assertEquals(1, response.shardResults.single().rowCount)
        assertEquals(true, response.shardResults.single().truncated)
    }

    @Test
    fun `maps sql console execution snapshot responses`() {
        val startedAt = Instant.now()
        val finishedAt = startedAt.plusSeconds(3)
        val snapshot = SqlConsoleExecutionSnapshot(
            id = "run-1",
            status = SqlConsoleExecutionStatus.SUCCESS,
            startedAt = startedAt,
            finishedAt = finishedAt,
            result = SqlConsoleQueryResult(
                sql = "delete from test",
                statementType = SqlConsoleStatementType.COMMAND,
                statementKeyword = "DELETE",
                maxRowsPerShard = 10,
                shardResults = listOf(
                    RawShardExecutionResult(
                        shardName = "db1",
                        status = "SUCCESS",
                        affectedRows = 4,
                        message = "DELETE выполнен успешно.",
                    ),
                ),
            ),
        )

        val startResponse = snapshot.toStartResponse()
        val response = snapshot.toResponse()

        assertEquals("run-1", startResponse.id)
        assertEquals("SUCCESS", startResponse.status)
        assertEquals("run-1", response.id)
        assertEquals("SUCCESS", response.status)
        assertEquals("DELETE", response.result?.statementKeyword)
        assertEquals(4, response.result?.shardResults?.single()?.affectedRows)
    }
}
