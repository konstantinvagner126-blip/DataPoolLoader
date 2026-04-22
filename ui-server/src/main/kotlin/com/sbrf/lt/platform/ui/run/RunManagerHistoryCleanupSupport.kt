package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupResultResponse
import java.time.Instant

internal class RunManagerHistoryCleanupSupport(
    private val stateStore: RunStateStore,
    private val objectMapper: ObjectMapper,
) {
    private val cleanupPlanningSupport = RunManagerHistoryCleanupPlanningSupport(
        stateStore = stateStore,
        objectMapper = objectMapper,
    )
    private val historyUsageSupport = RunManagerHistoryUsageSupport(objectMapper)
    private val previewResponseSupport = RunManagerHistoryCleanupPreviewResponseSupport(
        planningSupport = cleanupPlanningSupport,
        historyUsageSupport = historyUsageSupport,
    )
    private val executionSupport = RunManagerHistoryCleanupExecutionSupport(
        planningSupport = cleanupPlanningSupport,
    )

    fun previewHistoryCleanup(
        snapshots: List<MutableRunSnapshot>,
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ) = previewResponseSupport.buildPreviewResponse(
        snapshots = snapshots,
        cutoffTimestamp = cutoffTimestamp,
        retentionDays = retentionDays,
        keepMinRunsPerModule = keepMinRunsPerModule,
        disableSafeguard = disableSafeguard,
    )

    fun executeHistoryCleanup(
        snapshots: MutableList<MutableRunSnapshot>,
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ) = executionSupport.executeHistoryCleanup(
        snapshots = snapshots,
        cutoffTimestamp = cutoffTimestamp,
        retentionDays = retentionDays,
        keepMinRunsPerModule = keepMinRunsPerModule,
        disableSafeguard = disableSafeguard,
    )
}

internal data class HistoryCleanupExecutionResult(
    val deleted: Boolean,
    val response: RunHistoryCleanupResultResponse,
)
