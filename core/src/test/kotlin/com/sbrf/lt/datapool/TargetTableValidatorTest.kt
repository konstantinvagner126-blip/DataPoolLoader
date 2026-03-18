package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.db.TargetColumn
import com.sbrf.lt.datapool.db.TargetTableValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TargetTableValidatorTest {
    private val validator = TargetTableValidator()

    @Test
    fun `accepts compatible target columns`() {
        validator.validateCompatibility(
            targetTable = "public.test_data_pool",
            targetColumns = listOf(
                TargetColumn("id", nullable = false, hasDefault = false),
                TargetColumn("payload", nullable = true, hasDefault = false),
                TargetColumn("created_at", nullable = false, hasDefault = true),
            ),
            incomingColumns = listOf("id", "payload"),
        )
    }

    @Test
    fun `fails when incoming column is absent in target table`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validator.validateCompatibility(
                targetTable = "public.test_data_pool",
                targetColumns = listOf(TargetColumn("id", nullable = false, hasDefault = false)),
                incomingColumns = listOf("id", "payload"),
            )
        }

        assertEquals(
            "В целевой таблице public.test_data_pool отсутствуют колонки из входных данных: payload",
            error.message,
        )
    }

    @Test
    fun `fails when required target column is missing in incoming data`() {
        val error = assertFailsWith<IllegalArgumentException> {
            validator.validateCompatibility(
                targetTable = "public.test_data_pool",
                targetColumns = listOf(
                    TargetColumn("id", nullable = false, hasDefault = false),
                    TargetColumn("payload", nullable = false, hasDefault = false),
                ),
                incomingColumns = listOf("id"),
            )
        }

        assertEquals(
            "В целевой таблице public.test_data_pool есть обязательные NOT NULL колонки, отсутствующие во входных данных: payload",
            error.message,
        )
    }

    @Test
    fun `parses default schema when table schema omitted`() {
        val tableRef = validator.parseTableReference("test_data_pool")

        assertEquals("public", tableRef.schema)
        assertEquals("test_data_pool", tableRef.table)
    }
}
