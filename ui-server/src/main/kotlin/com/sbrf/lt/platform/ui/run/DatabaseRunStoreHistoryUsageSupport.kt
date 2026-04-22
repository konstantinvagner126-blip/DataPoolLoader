package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.CurrentStorageModuleResponse
import java.sql.Connection
import java.time.Instant

internal class DatabaseRunStoreHistoryUsageSupport(
    private val normalizedSchema: String,
) {
    private val storageUsageSupport = DatabaseRunStoreHistoryStorageUsageSupport(normalizedSchema)
    private val outputUsageCandidateSupport = DatabaseRunStoreOutputUsageCandidateSupport(normalizedSchema)

    fun loadCurrentHistoryStorageUsage(connection: Connection): DatabaseHistoryStorageUsage =
        storageUsageSupport.loadCurrentHistoryStorageUsage(connection)

    fun listCurrentOutputUsageCandidates(connection: Connection): List<OutputRetentionRunRef> =
        outputUsageCandidateSupport.listCurrentOutputUsageCandidates(connection)
}

internal data class DatabaseHistoryStorageUsage(
    val totalRuns: Int = 0,
    val totalModules: Int = 0,
    val totalStorageBytes: Long = 0,
    val oldestRequestedAt: Instant? = null,
    val newestRequestedAt: Instant? = null,
    val topModules: List<CurrentStorageModuleResponse> = emptyList(),
)
