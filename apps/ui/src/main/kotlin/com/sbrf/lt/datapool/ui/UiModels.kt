package com.sbrf.lt.datapool.ui

import com.sbrf.lt.datapool.app.ExecutionEvent
import com.sbrf.lt.datapool.model.ExecutionStatus
import java.nio.file.Path
import java.time.Instant

data class ModuleDescriptor(
    val id: String,
    val title: String,
    val configFile: Path,
    val resourcesDir: Path,
)

data class ModuleFileContent(
    val path: String,
    val content: String,
    val exists: Boolean,
)

data class ModuleDetailsResponse(
    val id: String,
    val title: String,
    val configPath: String,
    val configText: String,
    val sqlFiles: List<ModuleFileContent>,
)

data class SaveModuleRequest(
    val configText: String,
    val sqlFiles: Map<String, String>,
)

data class StartRunRequest(
    val moduleId: String,
    val configText: String,
    val sqlFiles: Map<String, String>,
)

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
    val events: List<ExecutionEvent> = emptyList(),
)

data class UiStateResponse(
    val activeRun: UiRunSnapshot? = null,
    val history: List<UiRunSnapshot> = emptyList(),
)

data class SaveResultResponse(
    val message: String,
)
