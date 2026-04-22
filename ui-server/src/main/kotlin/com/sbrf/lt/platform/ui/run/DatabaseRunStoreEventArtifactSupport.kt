package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import java.nio.file.Path

internal class DatabaseRunStoreEventArtifactSupport(
    connectionProvider: DatabaseConnectionProvider,
    normalizedSchema: String,
    objectMapperWithTime: ObjectMapper,
) {
    private val eventPersistenceSupport = DatabaseRunStoreEventPersistenceSupport(
        connectionProvider = connectionProvider,
        normalizedSchema = normalizedSchema,
        objectMapperWithTime = objectMapperWithTime,
    )
    private val artifactPersistenceSupport = DatabaseRunStoreArtifactPersistenceSupport(
        connectionProvider = connectionProvider,
        normalizedSchema = normalizedSchema,
    )

    fun appendEvent(
        runId: String,
        seqNo: Int,
        stage: String,
        eventType: String,
        severity: String,
        sourceName: String?,
        message: String,
        payload: Map<String, Any?>,
    ) = eventPersistenceSupport.appendEvent(runId, seqNo, stage, eventType, severity, sourceName, message, payload)

    fun upsertArtifact(
        runId: String,
        artifactKind: String,
        artifactKey: String,
        filePath: String,
        storageStatus: String,
        fileSizeBytes: Long?,
        contentHash: String?,
    ) = artifactPersistenceSupport.upsertArtifact(
        runId = runId,
        artifactKind = artifactKind,
        artifactKey = artifactKey,
        filePath = filePath,
        storageStatus = storageStatus,
        fileSizeBytes = fileSizeBytes,
        contentHash = contentHash,
    )

    fun markArtifactDeleted(runId: String, artifactKind: String, artifactKey: String) =
        artifactPersistenceSupport.markArtifactDeleted(runId, artifactKind, artifactKey)

    fun fileSize(filePath: Path): Long? = artifactPersistenceSupport.fileSize(filePath)
}
