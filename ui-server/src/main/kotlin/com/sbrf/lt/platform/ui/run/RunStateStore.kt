package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.fileSize

class RunStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
) {
    private val stateFile: Path = storageDir.resolve("run-state.json")

    fun load(): PersistedRunState {
        if (!stateFile.exists()) {
            return PersistedRunState()
        }
        stateFile.inputStream().bufferedReader().use {
            val loaded = configLoader.objectMapper().readValue(it, PersistedRunState::class.java)
            return loaded.withRecoveredInterruptedRuns()
        }
    }

    fun save(state: PersistedRunState) {
        storageDir.createDirectories()
        val tempFile = storageDir.resolve("run-state.json.tmp")
        tempFile.outputStream().bufferedWriter().use {
            configLoader.objectMapper().writerWithDefaultPrettyPrinter().writeValue(it, state)
        }
        Files.move(
            tempFile,
            stateFile,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    fun currentFileSizeBytes(): Long =
        if (stateFile.exists()) stateFile.fileSize() else 0L
}
