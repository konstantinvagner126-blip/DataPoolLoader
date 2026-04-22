package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import java.sql.Timestamp
import java.time.Instant

internal class DatabaseRunStoreRunCompletionMutationSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val normalizedSchema: String,
) {
    fun finishRun(
        runId: String,
        finishedAt: Instant,
        status: String,
        mergedRowCount: Long?,
        successfulSourceCount: Int,
        failedSourceCount: Int,
        skippedSourceCount: Int,
        targetStatus: String,
        targetTableName: String?,
        targetRowsLoaded: Long?,
        summaryJson: String,
        errorMessage: String?,
    ) {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.finishRun(normalizedSchema)).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(finishedAt))
                stmt.setString(2, status)
                setNullableLong(stmt, 3, mergedRowCount)
                stmt.setInt(4, successfulSourceCount)
                stmt.setInt(5, failedSourceCount)
                stmt.setInt(6, skippedSourceCount)
                stmt.setString(7, targetStatus)
                stmt.setString(8, targetTableName)
                setNullableLong(stmt, 9, targetRowsLoaded)
                stmt.setString(10, summaryJson)
                stmt.setString(11, errorMessage)
                stmt.setString(12, runId)
                stmt.executeUpdate()
            }
        }
    }
}
