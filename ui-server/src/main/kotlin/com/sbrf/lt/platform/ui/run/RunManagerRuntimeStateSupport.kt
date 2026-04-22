package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.platform.ui.model.UiStateResponse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal class RunManagerRuntimeStateSupport(
    private val persistenceSupport: RunManagerPersistenceSupport,
    private val stateSupport: RunManagerStateSupport,
    private val credentialsProvider: UiCredentialsProvider,
) {
    private val snapshots = mutableListOf<MutableRunSnapshot>()
    private val updatesFlow = MutableSharedFlow<UiStateResponse>(replay = 1, extraBufferCapacity = 32)

    init {
        restorePersistedState()
    }

    fun updates() = updatesFlow.asSharedFlow()

    fun currentState(): UiStateResponse = stateSupport.currentState(
        snapshots = snapshots,
        credentialsStatus = credentialsProvider.currentCredentialsStatus(),
    )

    fun snapshots(): MutableList<MutableRunSnapshot> = snapshots

    fun prependSnapshot(snapshot: MutableRunSnapshot) {
        snapshots.add(0, snapshot)
    }

    fun emitCurrentState() {
        updatesFlow.tryEmit(currentState())
    }

    fun publishState() {
        persistState()
        emitCurrentState()
    }

    private fun restorePersistedState() {
        snapshots.clear()
        snapshots.addAll(persistenceSupport.restoreSnapshots())
    }

    private fun persistState() {
        persistenceSupport.persistSnapshots(snapshots)
    }
}
