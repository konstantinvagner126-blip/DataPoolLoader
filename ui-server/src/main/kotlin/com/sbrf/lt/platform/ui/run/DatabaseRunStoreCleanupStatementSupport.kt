package com.sbrf.lt.platform.ui.run

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.Instant

internal class DatabaseRunStoreCleanupStatementSupport {
    fun prepareCleanupStatement(
        connection: Connection,
        sql: String,
        cutoffTimestamp: Instant,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
        includeSnapshotCutoff: Boolean = false,
    ): PreparedStatement =
        connection.prepareStatement(sql).apply {
            var index = 1
            setTimestamp(index++, Timestamp.from(cutoffTimestamp))
            setBoolean(index++, disableSafeguard)
            setInt(index++, keepMinRunsPerModule)
            if (includeSnapshotCutoff) {
                setTimestamp(index, Timestamp.from(cutoffTimestamp))
            }
        }
}
