package com.sbrf.lt.platform.ui.model

import java.time.Instant

/**
 * Текущее потребление истории/output по модулю для экрана обслуживания.
 */
data class CurrentStorageModuleResponse(
    val moduleCode: String,
    val currentRunsCount: Int,
    val currentStorageBytes: Long,
    val currentOutputDirs: Int? = null,
    val oldestRequestedAt: Instant? = null,
    val newestRequestedAt: Instant? = null,
)
