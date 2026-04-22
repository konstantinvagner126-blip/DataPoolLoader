package com.sbrf.lt.platform.ui.run

import java.time.Instant

internal class DatabaseOutputRetentionPlanningSupport(
    private val runStore: DatabaseRunMaintenanceStore,
    private val keepMinRunsPerModule: Int,
) {
    fun buildCleanupPlan(
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

    fun buildCurrentUsagePlan(): OutputRetentionPlan =
        OutputRetentionPlanner.buildPlan(runStore.listCurrentOutputUsageCandidates())
}
