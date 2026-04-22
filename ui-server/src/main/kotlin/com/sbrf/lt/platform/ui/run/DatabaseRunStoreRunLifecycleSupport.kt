package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import java.time.Instant

internal class DatabaseRunStoreRunLifecycleSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val normalizedSchema: String,
) {
    private val activeRunQuerySupport = DatabaseRunStoreActiveRunQuerySupport(connectionProvider, normalizedSchema)
    private val runMutationSupport = DatabaseRunStoreRunMutationSupport(connectionProvider, normalizedSchema)

    fun hasActiveRun(moduleCode: String): Boolean = activeRunQuerySupport.hasActiveRun(moduleCode)

    fun activeRunIds(moduleCode: String): List<String> = activeRunQuerySupport.activeRunIds(moduleCode)

    fun createRun(
        context: DatabaseModuleRunContext,
        startedAt: Instant,
        outputDir: String,
    ) = runMutationSupport.createRun(context, startedAt, outputDir)

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
    ) = runMutationSupport.finishRun(
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
    ) = runMutationSupport.markRunFailed(runId, finishedAt, errorMessage)
}
