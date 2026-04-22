package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.sql.ExecutionSnapshotSql
import java.sql.Connection

/**
 * Persistence слой execution snapshot DB-модуля.
 */
internal class DatabaseModuleExecutionSnapshotPersistenceSupport {
    fun insertExecutionSnapshot(
        connection: Connection,
        normalizedSchema: String,
        executionSnapshotId: String,
        source: DatabaseExecutionSourceRow,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        snapshotJson: String,
        contentHash: String,
    ) {
        connection.prepareStatement(ExecutionSnapshotSql.insertExecutionSnapshot(normalizedSchema)).use { stmt ->
            stmt.setString(1, executionSnapshotId)
            stmt.setString(2, source.moduleId)
            stmt.setString(3, actorId)
            stmt.setString(4, actorSource)
            stmt.setString(5, actorDisplayName)
            stmt.setString(6, source.sourceRevisionId)
            stmt.setString(7, source.sourceWorkingCopyId)
            stmt.setString(8, snapshotJson)
            stmt.setString(9, source.configText)
            stmt.setString(10, contentHash)
            stmt.executeUpdate()
        }
    }
}
