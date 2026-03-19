package com.sbrf.lt.datapool.app

import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.datapool.model.MergeMode
import java.nio.file.Path
import java.time.Instant

fun interface ExecutionListener {
    fun onEvent(event: ExecutionEvent)
}

sealed interface ExecutionEvent {
    val timestamp: Instant
}

data class RunStartedEvent(
    override val timestamp: Instant,
    val configPath: String,
    val outputDir: String,
    val sourceNames: List<String>,
    val mergeMode: MergeMode,
    val targetEnabled: Boolean,
) : ExecutionEvent

data class SourceExportStartedEvent(
    override val timestamp: Instant,
    val sourceName: String,
) : ExecutionEvent

data class SourceExportProgressEvent(
    override val timestamp: Instant,
    val sourceName: String,
    val rowCount: Long,
) : ExecutionEvent

data class SourceExportFinishedEvent(
    override val timestamp: Instant,
    val sourceName: String,
    val status: ExecutionStatus,
    val rowCount: Long,
    val columns: List<String>,
    val outputFile: String?,
    val errorMessage: String? = null,
) : ExecutionEvent

data class SourceSchemaMismatchEvent(
    override val timestamp: Instant,
    val sourceName: String,
    val expectedColumns: List<String>,
    val actualColumns: List<String>,
) : ExecutionEvent

data class MergeStartedEvent(
    override val timestamp: Instant,
    val mergeMode: MergeMode,
    val sourceNames: List<String>,
    val outputFile: String,
) : ExecutionEvent

data class MergeFinishedEvent(
    override val timestamp: Instant,
    val rowCount: Long,
    val outputFile: String,
    val sourceCounts: Map<String, Long>,
) : ExecutionEvent

data class TargetImportStartedEvent(
    override val timestamp: Instant,
    val table: String,
    val expectedRowCount: Long,
) : ExecutionEvent

data class TargetImportFinishedEvent(
    override val timestamp: Instant,
    val table: String,
    val status: ExecutionStatus,
    val rowCount: Long,
    val errorMessage: String? = null,
) : ExecutionEvent

data class OutputCleanupEvent(
    override val timestamp: Instant,
    val fileName: String,
) : ExecutionEvent

data class RunFinishedEvent(
    override val timestamp: Instant,
    val status: ExecutionStatus,
    val mergedRowCount: Long,
    val outputDir: String,
    val summaryFile: String?,
    val errorMessage: String? = null,
) : ExecutionEvent

data class ApplicationRunResult(
    val status: ExecutionStatus,
    val outputDir: Path,
    val mergedRowCount: Long,
    val summaryFile: Path?,
    val errorMessage: String? = null,
)

object NoOpExecutionListener : ExecutionListener {
    override fun onEvent(event: ExecutionEvent) = Unit
}
