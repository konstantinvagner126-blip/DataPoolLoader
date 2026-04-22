package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

internal class DatabaseRunStoreRunCreationSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val normalizedSchema: String,
) {
    fun createRun(
        context: DatabaseModuleRunContext,
        startedAt: Instant,
        outputDir: String,
    ) {
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.prepareStatement(RunHistorySql.insertRun(normalizedSchema)).use { stmt ->
                    stmt.setString(1, context.runId)
                    stmt.setString(2, context.actorId)
                    stmt.setString(3, context.actorSource)
                    stmt.setString(4, context.actorDisplayName)
                    stmt.setTimestamp(5, Timestamp.from(context.requestedAt))
                    stmt.setTimestamp(6, Timestamp.from(startedAt))
                    stmt.setString(7, context.runtimeSnapshot.launchSourceKind)
                    stmt.setString(8, context.runtimeSnapshot.moduleCode ?: error("moduleCode is required for DB run"))
                    stmt.setString(9, context.runtimeSnapshot.moduleTitle ?: context.runtimeSnapshot.moduleCode ?: "DB module")
                    stmt.setString(10, outputDir)
                    stmt.setString(11, context.runtimeSnapshot.appConfig.mergeMode.name)
                    stmt.setBoolean(12, context.runtimeSnapshot.appConfig.target.enabled)
                    stmt.setString(13, if (context.runtimeSnapshot.appConfig.target.enabled) "PENDING" else "NOT_ENABLED")
                    stmt.setString(14, context.runtimeSnapshot.appConfig.target.table.takeIf { it.isNotBlank() })
                    stmt.setString(15, context.runtimeSnapshot.executionSnapshotId ?: error("executionSnapshotId is required for DB run"))
                    stmt.executeUpdate()
                }

                context.sourceOrder.entries.sortedBy { it.value }.forEach { (sourceName, sortOrder) ->
                    connection.prepareStatement(RunHistorySql.insertSourceResult(normalizedSchema)).use { stmt ->
                        stmt.setString(1, UUID.randomUUID().toString())
                        stmt.setString(2, context.runId)
                        stmt.setString(3, sourceName)
                        stmt.setInt(4, sortOrder)
                        stmt.executeUpdate()
                    }
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
