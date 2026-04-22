package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import java.sql.Connection

internal class DatabaseRunStoreOutputUsageCandidateSupport(
    private val normalizedSchema: String,
) {
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
