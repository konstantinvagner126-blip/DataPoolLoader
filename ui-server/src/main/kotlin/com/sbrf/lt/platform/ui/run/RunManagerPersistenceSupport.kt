package com.sbrf.lt.platform.ui.run

internal class RunManagerPersistenceSupport(
    private val stateStore: RunStateStore,
) {
    fun restoreSnapshots(): List<MutableRunSnapshot> {
        val persisted = stateStore.load()
        return persisted.history.map { snapshot ->
            MutableRunSnapshot(
                id = snapshot.id,
                moduleId = snapshot.moduleId,
                moduleTitle = snapshot.moduleTitle,
                status = snapshot.status,
                startedAt = snapshot.startedAt,
                finishedAt = snapshot.finishedAt,
                outputDir = snapshot.outputDir,
                mergedRowCount = snapshot.mergedRowCount,
                summaryJson = snapshot.summaryJson,
                errorMessage = snapshot.errorMessage,
                sourceProgress = snapshot.sourceProgress.toMutableMap(),
                events = snapshot.events.toMutableList(),
            )
        }
    }

    fun persistSnapshots(snapshots: List<MutableRunSnapshot>) {
        stateStore.save(
            PersistedRunState(
                history = snapshots
                    .sortedByDescending { it.startedAt }
                    .map { it.toUi() },
            ),
        )
    }
}
