package com.example.datapoolloader

import com.example.datapoolloader.merge.MergeService
import com.example.datapoolloader.model.ExecutionStatus
import com.example.datapoolloader.model.MergeMode
import com.example.datapoolloader.model.SourceExecutionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Files
import java.time.Instant

class MergeServiceTest {
    private val service = MergeService()

    @Test
    fun `merges plain mode`() {
        val dir = Files.createTempDirectory("merge-plain")
        val source1 = createSource(dir, "db1", listOf("id,name", "1,A", "2,B"))
        val source2 = createSource(dir, "db2", listOf("id,name", "3,C"))
        val merged = dir.resolve("merged.csv")

        val rowCount = service.merge(listOf(source1, source2), MergeMode.PLAIN, merged)

        assertEquals(3, rowCount)
        assertEquals(listOf("id,name", "1,A", "2,B", "3,C"), Files.readAllLines(merged))
    }

    @Test
    fun `merges round robin mode`() {
        val dir = Files.createTempDirectory("merge-rr")
        val source1 = createSource(dir, "db1", listOf("id,name", "1,A", "2,B", "3,C"))
        val source2 = createSource(dir, "db2", listOf("id,name", "4,D", "5,E"))
        val source3 = createSource(dir, "db3", listOf("id,name", "6,F"))
        val merged = dir.resolve("merged.csv")

        val rowCount = service.merge(listOf(source1, source2, source3), MergeMode.ROUND_ROBIN, merged)

        assertEquals(6, rowCount)
        assertEquals(listOf("id,name", "1,A", "4,D", "6,F", "2,B", "5,E", "3,C"), Files.readAllLines(merged))
    }

    private fun createSource(
        dir: java.nio.file.Path,
        name: String,
        lines: List<String>,
    ): SourceExecutionResult {
        val file = dir.resolve("$name.csv")
        Files.write(file, lines)
        return SourceExecutionResult(
            sourceName = name,
            status = ExecutionStatus.SUCCESS,
            rowCount = (lines.size - 1).toLong(),
            outputFile = file,
            columns = listOf("id", "name"),
            startedAt = Instant.now(),
            finishedAt = Instant.now(),
        )
    }
}
