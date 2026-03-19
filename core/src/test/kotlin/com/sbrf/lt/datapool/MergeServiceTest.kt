package com.sbrf.lt.datapool

import com.sbrf.lt.datapool.merge.MergeService
import com.sbrf.lt.datapool.model.AppConfig
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.datapool.model.MergeMode
import com.sbrf.lt.datapool.model.SourceExecutionResult
import com.sbrf.lt.datapool.model.SourceQuotaConfig
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

        val result = service.merge(listOf(source1, source2), AppConfig(mergeMode = MergeMode.PLAIN), merged)

        assertEquals(3, result.rowCount)
        assertEquals(2, result.sourceCounts["db1"])
        assertEquals(listOf("id,name", "1,A", "2,B", "3,C"), Files.readAllLines(merged))
    }

    @Test
    fun `merges round robin mode`() {
        val dir = Files.createTempDirectory("merge-rr")
        val source1 = createSource(dir, "db1", listOf("id,name", "1,A", "2,B", "3,C"))
        val source2 = createSource(dir, "db2", listOf("id,name", "4,D", "5,E"))
        val source3 = createSource(dir, "db3", listOf("id,name", "6,F"))
        val merged = dir.resolve("merged.csv")

        val result = service.merge(listOf(source1, source2, source3), AppConfig(mergeMode = MergeMode.ROUND_ROBIN), merged)

        assertEquals(6, result.rowCount)
        assertEquals(listOf("id,name", "1,A", "4,D", "6,F", "2,B", "5,E", "3,C"), Files.readAllLines(merged))
    }

    @Test
    fun `merges proportional mode with even spread`() {
        val dir = Files.createTempDirectory("merge-proportional")
        val source1 = createSource(dir, "db1", listOf("id,name", "1,A", "2,B"))
        val source2 = createSource(dir, "db2", listOf("id,name", "3,C", "4,D", "5,E", "6,F"))
        val merged = dir.resolve("merged.csv")

        val result = service.merge(listOf(source1, source2), AppConfig(mergeMode = MergeMode.PROPORTIONAL), merged)

        assertEquals(6, result.rowCount)
        assertEquals(2, result.sourceCounts["db1"])
        assertEquals(4, result.sourceCounts["db2"])
        assertEquals(listOf("id,name", "1,A", "3,C", "4,D", "2,B", "5,E", "6,F"), Files.readAllLines(merged))
    }

    @Test
    fun `merges quota mode with limited total volume`() {
        val dir = Files.createTempDirectory("merge-quota")
        val source1 = createSource(dir, "db1", listOf("id,name", "1,A", "2,B"))
        val source2 = createSource(dir, "db2", listOf("id,name", "3,C", "4,D", "5,E", "6,F", "7,G", "8,H"))
        val merged = dir.resolve("merged.csv")

        val result = service.merge(
            listOf(source1, source2),
            AppConfig(
                mergeMode = MergeMode.QUOTA,
                quotas = listOf(
                    SourceQuotaConfig(source = "db1", percent = 25.0),
                    SourceQuotaConfig(source = "db2", percent = 75.0),
                ),
            ),
            merged,
        )

        assertEquals(8, result.rowCount)
        assertEquals(2, result.sourceCounts["db1"])
        assertEquals(6, result.sourceCounts["db2"])
        assertEquals(2, Files.readAllLines(merged).count { it.endsWith(",A") || it.endsWith(",B") })
    }

    @Test
    fun `respects max merged rows`() {
        val dir = Files.createTempDirectory("merge-max")
        val source1 = createSource(dir, "db1", listOf("id,name", "1,A", "2,B", "3,C"))
        val source2 = createSource(dir, "db2", listOf("id,name", "4,D", "5,E", "6,F"))
        val merged = dir.resolve("merged.csv")

        val result = service.merge(
            listOf(source1, source2),
            AppConfig(mergeMode = MergeMode.PROPORTIONAL, maxMergedRows = 4),
            merged,
        )

        assertEquals(4, result.rowCount)
        assertEquals(2, result.sourceCounts["db1"])
        assertEquals(2, result.sourceCounts["db2"])
    }

    @Test
    fun `uses reopenable round robin when sources exceed open readers limit`() {
        val dir = Files.createTempDirectory("merge-many")
        val sources = (1..65).map { index ->
            createSource(dir, "db$index", listOf("id,name", "$index,N$index"))
        }
        val merged = dir.resolve("merged.csv")

        val result = service.merge(
            sources,
            AppConfig(mergeMode = MergeMode.ROUND_ROBIN),
            merged,
        )

        assertEquals(65, result.rowCount)
        assertEquals(66, Files.readAllLines(merged).size)
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
