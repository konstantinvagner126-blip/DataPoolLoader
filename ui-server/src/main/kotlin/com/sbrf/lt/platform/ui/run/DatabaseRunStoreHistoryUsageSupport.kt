package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import com.sbrf.lt.platform.ui.model.CurrentStorageModuleResponse
import java.sql.Connection
import java.time.Instant

internal class DatabaseRunStoreHistoryUsageSupport(
    private val normalizedSchema: String,
) {
    fun loadCurrentHistoryStorageUsage(connection: Connection): DatabaseHistoryStorageUsage {
        val overview = connection.prepareStatement(
            RunHistorySql.currentHistoryStorageOverview(normalizedSchema),
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                if (!rs.next()) {
                    DatabaseHistoryStorageUsage()
                } else {
                    DatabaseHistoryStorageUsage(
                        totalRuns = rs.getInt("total_runs"),
                        totalModules = rs.getInt("total_modules"),
                        oldestRequestedAt = rs.getTimestamp("oldest_requested_at")?.toInstant(),
                        newestRequestedAt = rs.getTimestamp("newest_requested_at")?.toInstant(),
                    )
                }
            }
        }
        val totalBytes = connection.prepareStatement(
            RunHistorySql.currentHistoryStorageBytes(normalizedSchema),
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                if (!rs.next()) 0L else rs.getLong("total_history_storage_bytes")
            }
        }
        val topModules = connection.prepareStatement(
            RunHistorySql.currentHistoryStorageTopModules(normalizedSchema),
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            CurrentStorageModuleResponse(
                                moduleCode = rs.getString("module_code"),
                                currentRunsCount = rs.getInt("current_runs_count"),
                                currentStorageBytes = rs.getLong("current_storage_bytes"),
                                oldestRequestedAt = rs.getTimestamp("oldest_requested_at")?.toInstant(),
                                newestRequestedAt = rs.getTimestamp("newest_requested_at")?.toInstant(),
                            ),
                        )
                    }
                }
            }
        }
        return overview.copy(totalStorageBytes = totalBytes, topModules = topModules)
    }

    fun listCurrentOutputUsageCandidates(connection: Connection): List<OutputRetentionRunRef> =
        connection.prepareStatement(
            RunHistorySql.listCurrentOutputUsageCandidates(normalizedSchema),
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            OutputRetentionRunRef(
                                moduleCode = rs.getString("module_code"),
                                requestedAt = rs.getTimestamp("requested_at").toInstant(),
                                outputDir = rs.getString("output_dir"),
                            ),
                        )
                    }
                }
            }
        }
}

internal data class DatabaseHistoryStorageUsage(
    val totalRuns: Int = 0,
    val totalModules: Int = 0,
    val totalStorageBytes: Long = 0,
    val oldestRequestedAt: Instant? = null,
    val newestRequestedAt: Instant? = null,
    val topModules: List<CurrentStorageModuleResponse> = emptyList(),
)
