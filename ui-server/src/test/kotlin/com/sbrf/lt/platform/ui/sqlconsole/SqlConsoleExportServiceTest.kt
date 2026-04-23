package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.platform.ui.model.SqlConsoleQueryResponse
import com.sbrf.lt.platform.ui.model.SqlConsoleShardResultResponse
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqlConsoleExportServiceTest {

    private val service = SqlConsoleExportService()

    @Test
    fun `exports shard csv with escaping`() {
        val bytes = service.exportShardCsv(
            result = resultSetResponse(
                SqlConsoleShardResultResponse(
                    shardName = "db,1",
                    status = "SUCCESS",
                    columns = listOf("id", "text"),
                    rows = listOf(mapOf("id" to "1", "text" to "value, \"quoted\"\nnext")),
                ),
            ),
            shardName = "db,1",
        )

        assertEquals("id,text\n1,\"value, \"\"quoted\"\"\nnext\"\n", bytes.decodeToString())
    }

    @Test
    fun `export shard csv rejects unsupported statement or unknown shard`() {
        val typeError = assertFailsWith<IllegalArgumentException> {
            service.exportShardCsv(
                commandResponse(),
                "db1",
            )
        }
        assertTrue(typeError.message!!.contains("SELECT"))

        val shardError = assertFailsWith<SqlConsoleShardResultNotFoundException> {
            service.exportShardCsv(
                resultSetResponse(
                    SqlConsoleShardResultResponse(
                        shardName = "db1",
                        status = "FAILED",
                    ),
                ),
                "db1",
            )
        }
        assertTrue(shardError.message!!.contains("не найден"))
    }

    @Test
    fun `exports all successful shards to zip and rejects empty result`() {
        val bytes = service.exportAllZip(
            resultSetResponse(
                SqlConsoleShardResultResponse(
                    shardName = "db1",
                    status = "SUCCESS",
                    columns = listOf("id"),
                    rows = listOf(mapOf("id" to "1")),
                ),
                SqlConsoleShardResultResponse(
                    shardName = "db-2",
                    status = "SUCCESS",
                    columns = emptyList(),
                    rows = emptyList(),
                ),
                SqlConsoleShardResultResponse(
                    shardName = "db3",
                    status = "FAILED",
                ),
            ),
        )

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val names = mutableListOf<String>()
            val contents = mutableMapOf<String, String>()
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                contents[entry.name] = zip.readBytes().decodeToString()
            }
            assertEquals(listOf("db1.csv", "db-2.csv"), names)
            assertEquals("id\n1\n", contents["db1.csv"])
            assertEquals("", contents["db-2.csv"])
        }

        val noRowsError = assertFailsWith<IllegalArgumentException> {
            service.exportAllZip(
                resultSetResponse(
                    SqlConsoleShardResultResponse(shardName = "db1", status = "FAILED"),
                ),
            )
        }
        assertTrue(noRowsError.message!!.contains("Нет успешных"))

        val typeError = assertFailsWith<IllegalArgumentException> {
            service.exportAllZip(commandResponse())
        }
        assertTrue(typeError.message!!.contains("SELECT"))
    }

    private fun resultSetResponse(vararg shards: SqlConsoleShardResultResponse) = SqlConsoleQueryResponse(
        sql = "select 1",
        statementType = "RESULT_SET",
        statementKeyword = "SELECT",
        shardResults = shards.toList(),
        maxRowsPerShard = 200,
    )

    private fun commandResponse() = SqlConsoleQueryResponse(
        sql = "delete from demo",
        statementType = "COMMAND",
        statementKeyword = "DELETE",
        shardResults = emptyList(),
        maxRowsPerShard = 200,
    )
}
