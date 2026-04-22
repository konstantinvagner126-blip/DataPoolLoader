package com.sbrf.lt.platform.ui.run

import java.sql.Connection
import java.time.Instant

internal class DatabaseRunStoreCleanupCountSupport(
    private val statementSupport: DatabaseRunStoreCleanupStatementSupport,
) {
    fun queryCleanupCount(
        connection: Connection,
        sql: String,
        columnName: String,
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
        includeSnapshotCutoff: Boolean,
    ): Int =
        statementSupport.prepareCleanupStatement(
            connection = connection,
            sql = sql,
            cutoffTimestamp = cutoffTimestamp,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
            includeSnapshotCutoff = includeSnapshotCutoff,
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                if (!rs.next()) 0 else rs.getInt(columnName)
            }
        }
}
