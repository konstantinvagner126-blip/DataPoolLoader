package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.output.OutputRetentionPreviewResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionResultResponse
import java.time.Instant
import java.time.temporal.ChronoUnit

open class DatabaseOutputRetentionService(
    private val runStore: DatabaseRunMaintenanceStore,
    private val retentionDays: Int = 14,
    private val keepMinRunsPerModule: Int = 20,
) {
    open fun previewCleanup(disableSafeguard: Boolean = false): OutputRetentionPreviewResponse {
        val cutoffTimestamp = cleanupCutoff()
        val currentUsage = buildCurrentUsagePlan()
        val cleanupPlan = buildPlan(cutoffTimestamp, disableSafeguard)
        return OutputRetentionResponseSupport.buildPreviewResponse(
            storageMode = "DATABASE",
            disableSafeguard = disableSafeguard,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            cutoffTimestamp = cutoffTimestamp,
            currentUsage = currentUsage,
            cleanupPlan = cleanupPlan,
        )
    }

    open fun executeCleanup(disableSafeguard: Boolean = false): OutputRetentionResultResponse {
        val cutoffTimestamp = cleanupCutoff()
        val cleanupPlan = buildPlan(cutoffTimestamp, disableSafeguard)
        val deleteResult = OutputRetentionPlanner.delete(cleanupPlan)
        return OutputRetentionResponseSupport.buildResultResponse(
            storageMode = "DATABASE",
            disableSafeguard = disableSafeguard,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            cutoffTimestamp = cutoffTimestamp,
            finishedAt = Instant.now(),
            cleanupPlan = cleanupPlan,
            deleteResult = deleteResult,
        )
    }

    private fun buildPlan(
        cutoffTimestamp: Instant,
        disableSafeguard: Boolean,
    ): OutputRetentionPlan =
        OutputRetentionPlanner.buildPlan(
            runStore.listOutputRetentionCandidates(
                cutoffTimestamp = cutoffTimestamp,
                keepMinRunsPerModule = keepMinRunsPerModule,
                disableSafeguard = disableSafeguard,
            ),
        )

    private fun buildCurrentUsagePlan(): OutputRetentionPlan =
        OutputRetentionPlanner.buildPlan(runStore.listCurrentOutputUsageCandidates())

    private fun cleanupCutoff(): Instant =
        Instant.now().minus(retentionDays.toLong(), ChronoUnit.DAYS)
}
