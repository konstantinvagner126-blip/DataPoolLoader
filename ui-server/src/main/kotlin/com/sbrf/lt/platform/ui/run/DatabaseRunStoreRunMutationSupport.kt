package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import java.time.Instant

internal class DatabaseRunStoreRunMutationSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val normalizedSchema: String,
) {
    private val creationSupport = DatabaseRunStoreRunCreationSupport(connectionProvider, normalizedSchema)
    private val completionSupport = DatabaseRunStoreRunCompletionMutationSupport(connectionProvider, normalizedSchema)
    private val failureSupport = DatabaseRunStoreRunFailureMutationSupport(connectionProvider, normalizedSchema)

    fun createRun(
        context: DatabaseModuleRunContext,
        startedAt: Instant,
        outputDir: String,
    ) = creationSupport.createRun(context, startedAt, outputDir)

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
    ) = completionSupport.finishRun(
        runId = runId,
        finishedAt = finishedAt,
        status = status,
        mergedRowCount = mergedRowCount,
        successfulSourceCount = successfulSourceCount,
        failedSourceCount = failedSourceCount,
        skippedSourceCount = skippedSourceCount,
        targetStatus = targetStatus,
        targetTableName = targetTableName,
        targetRowsLoaded = targetRowsLoaded,
        summaryJson = summaryJson,
        errorMessage = errorMessage,
    )

    fun markRunFailed(
        runId: String,
        finishedAt: Instant,
        errorMessage: String,
    ) = failureSupport.markRunFailed(runId, finishedAt, errorMessage)
}
