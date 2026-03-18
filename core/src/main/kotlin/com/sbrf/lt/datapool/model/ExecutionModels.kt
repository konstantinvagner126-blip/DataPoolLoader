package com.sbrf.lt.datapool.model

import java.nio.file.Path
import java.time.Instant

data class ExportTask(
    val source: SourceConfig,
    val resolvedJdbcUrl: String,
    val resolvedUsername: String,
    val resolvedPassword: String,
    val sql: String,
    val outputFile: Path,
    val fetchSize: Int,
    val progressLogEveryRows: Long,
)

data class SourceExecutionResult(
    val sourceName: String,
    val status: ExecutionStatus,
    val rowCount: Long,
    val outputFile: Path?,
    val columns: List<String>,
    val startedAt: Instant,
    val finishedAt: Instant,
    val errorMessage: String? = null,
)

enum class ExecutionStatus {
    SUCCESS,
    FAILED,
    SKIPPED_SCHEMA_MISMATCH,
    SKIPPED,
}

data class SummaryReport(
    val startedAt: Instant,
    val finishedAt: Instant,
    val mergeMode: MergeMode,
    val mergedRowCount: Long,
    val mergedFile: String?,
    val maxMergedRows: Long?,
    val mergeDetails: MergeSummary,
    val targetLoad: TargetLoadSummary,
    val successfulSources: List<SourceSummary>,
    val failedSources: List<SourceSummary>,
)

data class MergeSummary(
    val sourceAllocations: List<MergeSourceAllocation>,
)

data class MergeSourceAllocation(
    val sourceName: String,
    val availableRows: Long,
    val mergedRows: Long,
    val mergedPercent: Double,
)

data class MergeResult(
    val rowCount: Long,
    val sourceCounts: Map<String, Long>,
)

data class SourceSummary(
    val sourceName: String,
    val status: ExecutionStatus,
    val rowCount: Long,
    val outputFile: String?,
    val columns: List<String>,
    val startedAt: Instant,
    val finishedAt: Instant,
    val errorMessage: String? = null,
)

data class TargetLoadSummary(
    val table: String,
    val status: ExecutionStatus,
    val rowCount: Long,
    val finishedAt: Instant,
    val enabled: Boolean = true,
    val errorMessage: String? = null,
)
