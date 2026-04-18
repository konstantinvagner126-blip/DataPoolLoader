package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.platform.ui.model.UiRunSnapshot
import java.time.Instant

/**
 * Изменяемое внутреннее представление текущего или исторического запуска в памяти UI.
 */
internal data class MutableRunSnapshot(
    val id: String,
    val moduleId: String,
    val moduleTitle: String,
    var status: ExecutionStatus,
    val startedAt: Instant,
    var finishedAt: Instant? = null,
    var outputDir: String? = null,
    var mergedRowCount: Long = 0,
    var summaryJson: String? = null,
    var errorMessage: String? = null,
    val sourceProgress: MutableMap<String, Long> = linkedMapOf(),
    val events: MutableList<Map<String, Any?>> = mutableListOf(),
) {
    fun toUi(): UiRunSnapshot = UiRunSnapshot(
        id = id,
        moduleId = moduleId,
        moduleTitle = moduleTitle,
        status = status,
        startedAt = startedAt,
        finishedAt = finishedAt,
        outputDir = outputDir,
        mergedRowCount = mergedRowCount,
        summaryJson = summaryJson,
        errorMessage = errorMessage,
        sourceProgress = sourceProgress.toMap(),
        events = events.toList(),
    )
}
