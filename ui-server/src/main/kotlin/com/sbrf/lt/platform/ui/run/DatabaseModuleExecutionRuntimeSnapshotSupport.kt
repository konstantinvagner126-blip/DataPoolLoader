package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.RuntimeModuleSnapshot
import com.sbrf.lt.platform.ui.module.DatabaseModuleSnapshotSupport
import java.sql.Connection
import java.util.UUID

internal data class PreparedDatabaseExecutionSnapshot(
    val source: DatabaseExecutionSourceRow,
    val executionSnapshotId: String,
    val runtimeSnapshot: RuntimeModuleSnapshot,
    val snapshotJson: String,
    val contentHash: String,
)

internal class DatabaseModuleExecutionRuntimeSnapshotSupport(
    private val snapshotFactory: RuntimeConfigSnapshotFactory,
    private val snapshotSupport: DatabaseModuleSnapshotSupport,
    private val querySupport: DatabaseModuleExecutionQuerySupport,
) {
    fun prepareRuntimeSnapshot(
        connection: Connection,
        normalizedSchema: String,
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): PreparedDatabaseExecutionSnapshot {
        val source = querySupport.loadSource(connection, normalizedSchema, moduleCode, actorId, actorSource)
        val sqlFiles = querySupport.loadSqlFiles(connection, normalizedSchema, source)
        val executionSnapshotId = UUID.randomUUID().toString()
        val runtimeSnapshot = snapshotFactory.createSnapshot(
            moduleCode = source.moduleCode,
            moduleTitle = source.title,
            configText = source.configText,
            sqlFiles = sqlFiles,
            launchSourceKind = source.sourceKind,
            configLocation = "db:${source.moduleCode}#${source.sourceKind.lowercase()}",
            executionSnapshotId = executionSnapshotId,
        )
        val snapshotJson = snapshotSupport.serializeExecutionSnapshot(source.configText, sqlFiles)
        val contentHash = snapshotSupport.calculateExecutionContentHash(source.configText, sqlFiles)
        return PreparedDatabaseExecutionSnapshot(
            source = source,
            executionSnapshotId = executionSnapshotId,
            runtimeSnapshot = runtimeSnapshot,
            snapshotJson = snapshotJson,
            contentHash = contentHash,
        )
    }
}
