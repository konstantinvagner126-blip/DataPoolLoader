package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.platform.ui.model.CurrentStorageModuleResponse

internal class RunManagerHistoryUsageSupport(
    private val objectMapper: ObjectMapper,
) {
    fun buildCurrentHistoryUsageModules(
        snapshots: List<MutableRunSnapshot>,
    ): List<CurrentStorageModuleResponse> =
        snapshots
            .groupBy { it.moduleId }
            .map { (moduleCode, moduleSnapshots) ->
                CurrentStorageModuleResponse(
                    moduleCode = moduleCode,
                    currentRunsCount = moduleSnapshots.size,
                    currentStorageBytes = estimateHistoryStorageBytesForSnapshots(moduleSnapshots),
                    oldestRequestedAt = moduleSnapshots.minOfOrNull { it.startedAt },
                    newestRequestedAt = moduleSnapshots.maxOfOrNull { it.startedAt },
                )
            }
            .sortedWith(
                compareByDescending<CurrentStorageModuleResponse> { it.currentStorageBytes }
                    .thenByDescending { it.currentRunsCount }
                    .thenBy { it.moduleCode },
            )
            .take(5)

    private fun estimateHistoryStorageBytesForSnapshots(moduleSnapshots: List<MutableRunSnapshot>): Long =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(
            PersistedRunState(
                history = moduleSnapshots
                    .sortedByDescending { it.startedAt }
                    .map { it.toUi() },
            ),
        ).size.toLong()
}
