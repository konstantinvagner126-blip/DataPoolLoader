package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.output.OutputRetentionPreviewResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionResultResponse
import java.time.Instant

open class DatabaseOutputRetentionService(
    runStore: DatabaseRunMaintenanceStore,
    private val retentionDays: Int = 14,
    private val keepMinRunsPerModule: Int = 20,
) {
    private val planningSupport = DatabaseOutputRetentionPlanningSupport(runStore, keepMinRunsPerModule)
    private val policySupport = OutputRetentionPolicySupport(retentionDays)

    open fun previewCleanup(disableSafeguard: Boolean = false): OutputRetentionPreviewResponse {
        val cutoffTimestamp = policySupport.cleanupCutoff()
        val currentUsage = planningSupport.buildCurrentUsagePlan()
        val cleanupPlan = planningSupport.buildCleanupPlan(cutoffTimestamp, disableSafeguard)
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
        val cutoffTimestamp = policySupport.cleanupCutoff()
        val cleanupPlan = planningSupport.buildCleanupPlan(cutoffTimestamp, disableSafeguard)
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
}
