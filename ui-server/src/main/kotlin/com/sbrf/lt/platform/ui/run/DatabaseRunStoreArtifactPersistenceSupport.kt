package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

internal class DatabaseRunStoreArtifactPersistenceSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val normalizedSchema: String,
) {
    fun upsertArtifact(
        runId: String,
        artifactKind: String,
        artifactKey: String,
        filePath: String,
        storageStatus: String,
        fileSizeBytes: Long?,
        contentHash: String?,
    ) {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.upsertArtifact(normalizedSchema)).use { stmt ->
                stmt.setString(1, UUID.randomUUID().toString())
                stmt.setString(2, runId)
                stmt.setString(3, artifactKind)
                stmt.setString(4, artifactKey)
                stmt.setString(5, filePath)
                stmt.setString(6, storageStatus)
                setNullableLong(stmt, 7, fileSizeBytes)
                stmt.setString(8, contentHash)
                stmt.executeUpdate()
            }
        }
    }

    fun markArtifactDeleted(runId: String, artifactKind: String, artifactKey: String) {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.markArtifactDeleted(normalizedSchema)).use { stmt ->
                stmt.setString(1, runId)
                stmt.setString(2, artifactKind)
                stmt.setString(3, artifactKey)
                stmt.executeUpdate()
            }
        }
    }

    fun fileSize(filePath: Path): Long? = Files.size(filePath)
}
