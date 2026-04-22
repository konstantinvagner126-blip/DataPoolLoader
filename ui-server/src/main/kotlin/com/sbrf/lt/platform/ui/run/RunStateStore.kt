package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Path

class RunStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
) {
    private val stateFile: Path = storageDir.resolve("run-state.json")

    fun load(): PersistedRunState {
        return readOptionalRunStateFile(stateFile, configLoader, PersistedRunState::class.java)
            ?.withRecoveredInterruptedRuns()
            ?: PersistedRunState()
    }

    fun save(state: PersistedRunState) {
        saveRunStateFile(storageDir, stateFile, configLoader, state)
    }

    fun currentFileSizeBytes(): Long = runStateFileSizeBytes(stateFile)
}
