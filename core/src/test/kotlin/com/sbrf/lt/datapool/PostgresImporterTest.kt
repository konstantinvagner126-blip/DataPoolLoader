package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.db.PostgresTargetImporter
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.datapool.model.TargetConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.nio.file.Files
import java.sql.SQLException

class PostgresImporterTest {
    private val importer = PostgresTargetImporter()

    @Test
    fun `builds copy sql for schema qualified table`() {
        val sql = importer.buildCopySql(
            table = "public.test_data_pool",
            columns = listOf("id", "created_at", "payload"),
        )

        assertEquals(
            "COPY \"public\".\"test_data_pool\" (\"id\", \"created_at\", \"payload\") FROM STDIN WITH (FORMAT csv, HEADER true)",
            sql,
        )
    }

    @Test
    fun `rejects unsafe table identifier`() {
        assertFailsWith<IllegalArgumentException> {
            importer.buildCopySql("public.test-data", listOf("id"))
        }
    }

    @Test
    fun `builds truncate sql for schema qualified table`() {
        val sql = importer.buildTruncateSql("public.test_data_pool")

        assertEquals(
            "TRUNCATE TABLE \"public\".\"test_data_pool\"",
            sql,
        )
    }

    @Test
    fun `imports csv in one transaction with truncate`() {
        val state = FakeConnectionState()
        val importer = PostgresTargetImporter(
            connectionProvider = { _, _, _ -> importerConnection(state) },
            copyExecutor = { _, _, _ -> 3L },
        )
        val mergedFile = Files.createTempFile("merged", ".csv").apply {
            Files.writeString(this, "id\n1\n2\n3\n")
        }

        val result = importer.importCsv(
            target = TargetConfig(table = "public.test_data_pool", truncateBeforeLoad = true),
            resolvedJdbcUrl = "jdbc:test",
            resolvedUsername = "user",
            mergedFile = mergedFile,
            columns = listOf("id"),
            expectedRowCount = 3,
            resolvedPassword = "pwd",
        )

        assertEquals(ExecutionStatus.SUCCESS, result.status)
        assertEquals(1, state.committed)
        assertEquals(0, state.rolledBack)
        assertTrue(state.executedSql.single().startsWith("TRUNCATE TABLE"))
    }

    @Test
    fun `rolls back when copy fails`() {
        val state = FakeConnectionState()
        val importer = PostgresTargetImporter(
            connectionProvider = { _, _, _ -> importerConnection(state) },
            copyExecutor = { _, _, _ -> throw SQLException("copy failed") },
        )
        val mergedFile = Files.createTempFile("merged-fail", ".csv").apply {
            Files.writeString(this, "id\n1\n")
        }

        val result = importer.importCsv(
            target = TargetConfig(table = "public.test_data_pool", truncateBeforeLoad = true),
            resolvedJdbcUrl = "jdbc:test",
            resolvedUsername = "user",
            mergedFile = mergedFile,
            columns = listOf("id"),
            expectedRowCount = 1,
            resolvedPassword = "pwd",
        )

        assertEquals(ExecutionStatus.FAILED, result.status)
        assertEquals(0, state.committed)
        assertEquals(1, state.rolledBack)
        assertEquals("copy failed", result.errorMessage)
    }
}
