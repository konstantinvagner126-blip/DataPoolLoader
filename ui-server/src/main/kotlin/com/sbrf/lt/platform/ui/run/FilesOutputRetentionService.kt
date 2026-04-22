package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.platform.ui.model.output.OutputRetentionPreviewResponse
import com.sbrf.lt.platform.ui.model.output.OutputRetentionResultResponse
import java.time.Instant

class FilesOutputRetentionService(
    runManager: FilesModuleRunOperations,
    private val retentionDays: Int = 14,
    private val keepMinRunsPerModule: Int = 20,
) {
    private val planningSupport = FilesOutputRetentionPlanningSupport(runManager, keepMinRunsPerModule)
    private val policySupport = OutputRetentionPolicySupport(retentionDays)

    fun previewCleanup(disableSafeguard: Boolean = false): OutputRetentionPreviewResponse {
        val cutoffTimestamp = policySupport.cleanupCutoff()
        val currentUsage = planningSupport.buildCurrentUsagePlan()
        val cleanupPlan = planningSupport.buildCleanupPlan(cutoffTimestamp, disableSafeguard)
        return OutputRetentionResponseSupport.buildPreviewResponse(
            storageMode = "FILES",
            disableSafeguard = disableSafeguard,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            cutoffTimestamp = cutoffTimestamp,
            currentUsage = currentUsage,
            cleanupPlan = cleanupPlan,
        )
    }

    fun executeCleanup(disableSafeguard: Boolean = false): OutputRetentionResultResponse {
        val cutoffTimestamp = policySupport.cleanupCutoff()
        val cleanupPlan = planningSupport.buildCleanupPlan(cutoffTimestamp, disableSafeguard)
        val deleteResult = OutputRetentionPlanner.delete(cleanupPlan)
        return OutputRetentionResponseSupport.buildResultResponse(
            storageMode = "FILES",
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
