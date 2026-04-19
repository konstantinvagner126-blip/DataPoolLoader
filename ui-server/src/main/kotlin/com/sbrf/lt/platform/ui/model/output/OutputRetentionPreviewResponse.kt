package com.sbrf.lt.platform.ui.model.output

import com.sbrf.lt.platform.ui.model.CurrentStorageModuleResponse
import java.time.Instant

data class OutputRetentionPreviewResponse(
    val storageMode: String,
    val safeguardEnabled: Boolean,
    val retentionDays: Int,
    val keepMinRunsPerModule: Int,
    val cutoffTimestamp: Instant,
    val currentRunsWithOutput: Int = 0,
    val currentModulesWithOutput: Int = 0,
    val currentOutputDirs: Int = 0,
    val currentBytes: Long = 0,
    val currentOldestRequestedAt: Instant? = null,
    val currentNewestRequestedAt: Instant? = null,
    val currentTopModules: List<CurrentStorageModuleResponse> = emptyList(),
    val totalModulesAffected: Int = 0,
    val totalRunsAffected: Int = 0,
    val totalOutputDirsToDelete: Int = 0,
    val totalMissingOutputDirs: Int = 0,
    val totalBytesToFree: Long = 0,
    val modules: List<OutputRetentionModuleResponse> = emptyList(),
)
