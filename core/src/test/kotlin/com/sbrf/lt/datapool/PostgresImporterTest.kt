package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.db.PostgresImporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PostgresImporterTest {
    private val importer = PostgresImporter()

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
}
