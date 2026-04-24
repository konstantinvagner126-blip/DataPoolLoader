package com.sbrf.lt.platform.ui.run

import java.time.Instant

internal class DatabaseModuleRunRecoverySupport(
    private val runExecutionStore: DatabaseRunExecutionStore,
    private val runQueryStore: DatabaseRunQueryStore,
    private val activeRunRegistry: DatabaseModuleActiveRunRegistry,
    private val nowProvider: () -> Instant = Instant::now,
) {
    fun recoverAllOrphanRuns() {
        runQueryStore.activeModuleCodes().forEach(::recoverModuleOrphanRuns)
    }

    fun recoverModuleOrphanRuns(moduleCode: String) {
        val localActiveRunId = activeRunRegistry.currentRunId(moduleCode)
        runExecutionStore.activeRunIds(moduleCode)
            .filter { it != localActiveRunId }
            .forEach { runId ->
                runExecutionStore.markRunFailed(
                    runId = runId,
                    finishedAt = nowProvider(),
                    errorMessage = ORPHAN_DB_RUN_RECOVERY_MESSAGE,
                )
            }
    }
}

internal const val ORPHAN_DB_RUN_RECOVERY_MESSAGE =
    "DB-запуск был прерван до завершения и восстановлен как FAILED при следующем старте UI."
