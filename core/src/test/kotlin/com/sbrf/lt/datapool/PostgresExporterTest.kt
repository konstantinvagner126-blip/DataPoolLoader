package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.app.ExecutionEvent
import com.sbrf.lt.datapool.app.ExecutionListener
import com.sbrf.lt.datapool.app.SourceExportFinishedEvent
import com.sbrf.lt.datapool.app.SourceExportProgressEvent
import com.sbrf.lt.datapool.app.SourceExportStartedEvent
import com.sbrf.lt.datapool.db.PostgresExporter
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.datapool.model.ExportTask
import com.sbrf.lt.datapool.model.SourceConfig
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import java.nio.file.Files
import java.sql.SQLException

class PostgresExporterTest {

    @Test
    fun `exports rows to csv and emits progress events`() {
        val events = mutableListOf<ExecutionEvent>()
        val exporter = PostgresExporter { _, _, _ ->
            exportConnection(
                columns = listOf("id", "name"),
                rows = listOf(
                    listOf(1, "A"),
                    listOf(2, "B"),
                ),
            )
        }
        val outputFile = Files.createTempFile("export", ".csv")

        val result = exporter.export(
            ExportTask(
                source = SourceConfig(name = "db1"),
                resolvedJdbcUrl = "jdbc:test",
                resolvedUsername = "user",
                resolvedPassword = "pwd",
                sql = "select 1",
                outputFile = outputFile,
                fetchSize = 100,
                progressLogEveryRows = 1,
                executionListener = ExecutionListener { events += it },
            ),
        )

        assertEquals(ExecutionStatus.SUCCESS, result.status)
        assertEquals(2L, result.rowCount)
        assertEquals(listOf("id", "name"), result.columns)
        assertEquals(listOf("id,name", "1,A", "2,B"), outputFile.readLines())
        assertIs<SourceExportStartedEvent>(events.first())
        assertEquals(2, events.filterIsInstance<SourceExportProgressEvent>().size)
        assertIs<SourceExportFinishedEvent>(events.last())
    }

    @Test
    fun `returns failed result when export throws`() {
        val events = mutableListOf<ExecutionEvent>()
        val exporter = PostgresExporter { _, _, _ -> throw SQLException("boom") }
        val outputFile = Files.createTempFile("export-error", ".csv")

        val result = exporter.export(
            ExportTask(
                source = SourceConfig(name = "db1"),
                resolvedJdbcUrl = "jdbc:test",
                resolvedUsername = "user",
                resolvedPassword = "pwd",
                sql = "select 1",
                outputFile = outputFile,
                fetchSize = 100,
                progressLogEveryRows = 100,
                executionListener = ExecutionListener { events += it },
            ),
        )

        assertEquals(ExecutionStatus.FAILED, result.status)
        assertNull(result.outputFile)
        assertEquals("boom", result.errorMessage)
        val finished = events.last() as SourceExportFinishedEvent
        assertEquals(ExecutionStatus.FAILED, finished.status)
        assertEquals("boom", finished.errorMessage)
    }
}
