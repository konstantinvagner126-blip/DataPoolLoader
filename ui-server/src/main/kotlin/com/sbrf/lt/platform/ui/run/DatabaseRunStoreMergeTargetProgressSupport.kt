package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql

internal class DatabaseRunStoreMergeTargetProgressSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val normalizedSchema: String,
) {
    fun updateMergedRowCount(runId: String, mergedRowCount: Long) {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateMergedRowCount(normalizedSchema)).use { stmt ->
                stmt.setLong(1, mergedRowCount)
                stmt.setString(2, runId)
                stmt.executeUpdate()
            }
        }
    }

    fun updateTargetStatus(runId: String, targetStatus: String, targetTableName: String?, targetRowsLoaded: Long?) {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateTargetStatus(normalizedSchema)).use { stmt ->
                stmt.setString(1, targetStatus)
                stmt.setString(2, targetTableName)
                setNullableLong(stmt, 3, targetRowsLoaded)
                stmt.setString(4, runId)
                stmt.executeUpdate()
            }
        }
    }
}
