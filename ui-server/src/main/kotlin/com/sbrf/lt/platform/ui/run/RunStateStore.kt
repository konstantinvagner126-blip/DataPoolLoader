package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Path

class RunStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
) {
    private val stateFile: Path = storageDir.resolve("run-state.json")

    fun load(): PersistedRunState {
        val persistedState = readOptionalRunStateFile(stateFile, configLoader, PersistedRunState::class.java)
            ?: return PersistedRunState()
        val recoveredState = persistedState.withRecoveredInterruptedRuns()
        if (recoveredState != persistedState) {
            saveRunStateFile(storageDir, stateFile, configLoader, recoveredState)
        }
        return recoveredState
    }

    fun save(state: PersistedRunState) {
        saveRunStateFile(storageDir, stateFile, configLoader, state)
    }

    fun currentFileSizeBytes(): Long = runStateFileSizeBytes(stateFile)
}
