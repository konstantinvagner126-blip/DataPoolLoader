package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import java.sql.Timestamp
import java.time.Instant

internal class DatabaseRunStoreRunFailureMutationSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val normalizedSchema: String,
) {
    fun markRunFailed(
        runId: String,
        finishedAt: Instant,
        errorMessage: String,
    ) {
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.prepareStatement(RunHistorySql.markIncompleteSourcesFailed(normalizedSchema)).use { stmt ->
                    stmt.setTimestamp(1, Timestamp.from(finishedAt))
                    stmt.setString(2, errorMessage)
                    stmt.setString(3, runId)
                    stmt.executeUpdate()
                }
                connection.prepareStatement(RunHistorySql.markRunFailed(normalizedSchema)).use { stmt ->
                    stmt.setTimestamp(1, Timestamp.from(finishedAt))
                    stmt.setString(2, errorMessage)
                    stmt.setString(3, runId)
                    stmt.executeUpdate()
                }
                connection.commit()
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }
}
