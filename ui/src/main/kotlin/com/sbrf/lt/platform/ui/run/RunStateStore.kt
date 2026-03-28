package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.platform.ui.model.UiRunSnapshot
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

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
}

data class PersistedRunState(
    val uploadedCredentials: PersistedUploadedCredentials? = null,
    val history: List<UiRunSnapshot> = emptyList(),
) {
    fun withRecoveredInterruptedRuns(now: Instant = Instant.now()): PersistedRunState {
        val recovered = history.map { snapshot ->
            if (snapshot.status == ExecutionStatus.RUNNING) {
                snapshot.copy(
                    status = ExecutionStatus.FAILED,
                    finishedAt = snapshot.finishedAt ?: now,
                    errorMessage = snapshot.errorMessage ?: "UI был перезапущен до завершения запуска.",
                )
            } else {
                snapshot
            }
        }
        return copy(history = recovered)
    }
}

data class PersistedUploadedCredentials(
    val fileName: String,
    val content: String,
)
