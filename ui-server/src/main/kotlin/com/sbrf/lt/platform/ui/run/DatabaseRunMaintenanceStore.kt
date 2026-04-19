package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupResultResponse
import java.time.Instant

/**
 * Контракт cleanup/retention операций для DB run-history и output.
 */
interface DatabaseRunMaintenanceStore {
    fun previewCleanup(
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): DatabaseRunHistoryCleanupPreviewResponse

    fun executeCleanup(
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): DatabaseRunHistoryCleanupResultResponse

    fun listOutputRetentionCandidates(
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): List<OutputRetentionRunRef>

    fun listCurrentOutputUsageCandidates(): List<OutputRetentionRunRef>
}
