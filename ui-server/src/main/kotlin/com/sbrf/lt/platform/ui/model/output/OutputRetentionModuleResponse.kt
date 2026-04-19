package com.sbrf.lt.platform.ui.model.output

import java.time.Instant

data class OutputRetentionModuleResponse(
    val moduleCode: String,
    val totalRunsAffected: Int,
    val totalOutputDirsToDelete: Int,
    val totalBytesToFree: Long,
    val oldestRequestedAt: Instant? = null,
    val newestRequestedAt: Instant? = null,
)
