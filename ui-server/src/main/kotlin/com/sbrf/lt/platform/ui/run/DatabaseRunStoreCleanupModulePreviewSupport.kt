package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import com.sbrf.lt.platform.ui.model.DatabaseRunHistoryCleanupModuleResponse
import java.sql.Connection
import java.time.Instant

internal class DatabaseRunStoreCleanupModulePreviewSupport(
    private val normalizedSchema: String,
    private val statementSupport: DatabaseRunStoreCleanupStatementSupport,
) {
    fun loadModules(
        connection: Connection,
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): List<DatabaseRunHistoryCleanupModuleResponse> =
        statementSupport.prepareCleanupStatement(
            connection = connection,
            sql = RunHistorySql.listCleanupModules(normalizedSchema),
            cutoffTimestamp = cutoffTimestamp,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
            includeSnapshotCutoff = false,
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<DatabaseRunHistoryCleanupModuleResponse>()
                while (rs.next()) {
                    result += DatabaseRunHistoryCleanupModuleResponse(
                        moduleCode = rs.getString("module_code"),
                        totalRunsToDelete = rs.getInt("total_runs_to_delete"),
                        oldestRequestedAt = rs.getTimestamp("oldest_requested_at")?.toInstant(),
                        newestRequestedAt = rs.getTimestamp("newest_requested_at")?.toInstant(),
                    )
                }
                result
            }
        }
}
