package com.sbrf.lt.platform.ui.model.output

import java.time.Instant

data class OutputRetentionResultResponse(
    val storageMode: String,
    val safeguardEnabled: Boolean,
    val retentionDays: Int,
    val keepMinRunsPerModule: Int,
    val cutoffTimestamp: Instant,
    val finishedAt: Instant,
    val totalModulesAffected: Int = 0,
    val totalRunsAffected: Int = 0,
    val totalOutputDirsDeleted: Int = 0,
    val totalMissingOutputDirs: Int = 0,
    val totalBytesFreed: Long = 0,
    val modules: List<OutputRetentionModuleResponse> = emptyList(),
)
