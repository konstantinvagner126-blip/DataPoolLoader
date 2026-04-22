package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import java.sql.Timestamp
import java.time.Instant

internal class DatabaseRunStoreSourceProgressSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val normalizedSchema: String,
) {
    fun markSourceStarted(runId: String, sourceName: String, startedAt: Instant) {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateSourceStarted(normalizedSchema)).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(startedAt))
                stmt.setString(2, runId)
                stmt.setString(3, sourceName)
                stmt.executeUpdate()
            }
        }
    }

    fun updateSourceProgress(runId: String, sourceName: String, timestamp: Instant, exportedRowCount: Long) {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateSourceProgress(normalizedSchema)).use { stmt ->
                stmt.setLong(1, exportedRowCount)
                stmt.setTimestamp(2, Timestamp.from(timestamp))
                stmt.setString(3, runId)
                stmt.setString(4, sourceName)
                stmt.executeUpdate()
            }
        }
    }

    fun markSourceFinished(
        runId: String,
        sourceName: String,
        status: String,
        finishedAt: Instant,
        exportedRowCount: Long?,
        errorMessage: String?,
    ) {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateSourceFinished(normalizedSchema)).use { stmt ->
                stmt.setString(1, status)
                stmt.setTimestamp(2, Timestamp.from(finishedAt))
                setNullableLong(stmt, 3, exportedRowCount)
                stmt.setString(4, errorMessage)
                stmt.setString(5, runId)
                stmt.setString(6, sourceName)
                stmt.executeUpdate()
            }
        }
    }

    fun markSourceSkipped(runId: String, sourceName: String, finishedAt: Instant, message: String) {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateSourceMismatch(normalizedSchema)).use { stmt ->
                stmt.setTimestamp(1, Timestamp.from(finishedAt))
                stmt.setString(2, message)
                stmt.setString(3, runId)
                stmt.setString(4, sourceName)
                stmt.executeUpdate()
            }
        }
    }

    fun updateSourceMergedRows(runId: String, sourceName: String, mergedRowCount: Long) {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.updateSourceMergedRows(normalizedSchema)).use { stmt ->
                stmt.setLong(1, mergedRowCount)
                stmt.setString(2, runId)
                stmt.setString(3, sourceName)
                stmt.executeUpdate()
            }
        }
    }
}
