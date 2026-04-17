package com.sbrf.lt.platform.ui.model

import com.sbrf.lt.datapool.model.ExecutionStatus
import java.time.Instant

/**
 * Снимок одного запуска для файлового UI и WebSocket-обновлений.
 */
data class UiRunSnapshot(
    val id: String,
    val moduleId: String,
    val moduleTitle: String,
    val status: ExecutionStatus,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val outputDir: String? = null,
    val mergedRowCount: Long = 0,
    val summaryJson: String? = null,
    val errorMessage: String? = null,
    val sourceProgress: Map<String, Long> = emptyMap(),
    val events: List<Map<String, Any?>> = emptyList(),
)
