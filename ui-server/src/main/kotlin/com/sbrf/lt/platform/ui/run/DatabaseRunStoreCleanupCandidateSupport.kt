package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import java.sql.Connection
import java.time.Instant

internal class DatabaseRunStoreCleanupCandidateSupport(
    private val normalizedSchema: String,
    private val statementSupport: DatabaseRunStoreCleanupStatementSupport,
) {
    fun listOutputRetentionCandidates(
        connection: Connection,
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): List<OutputRetentionRunRef> =
        statementSupport.prepareCleanupStatement(
            connection = connection,
            sql = RunHistorySql.listCleanupOutputRetentionCandidates(normalizedSchema),
            cutoffTimestamp = cutoffTimestamp,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
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
