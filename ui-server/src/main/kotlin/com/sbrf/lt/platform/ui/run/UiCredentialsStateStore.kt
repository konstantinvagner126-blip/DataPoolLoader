package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

/**
 * Хранилище загруженного пользователем `credential.properties`.
 */
class UiCredentialsStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
) {
    private val stateFile: Path = storageDir.resolve("credentials-state.json")
    private val legacyRunStateFile: Path = storageDir.resolve("run-state.json")

    fun load(): PersistedCredentialsState {
        if (stateFile.exists()) {
            stateFile.inputStream().bufferedReader().use {
                return configLoader.objectMapper().readValue(it, PersistedCredentialsState::class.java)
            }
        }
        val migratedState = loadLegacyState()
        if (migratedState.uploadedCredentials != null) {
            save(migratedState)
        }
        return migratedState
    }

    fun save(state: PersistedCredentialsState) {
        storageDir.createDirectories()
        val tempFile = storageDir.resolve("credentials-state.json.tmp")
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

    private fun loadLegacyState(): PersistedCredentialsState {
        if (!legacyRunStateFile.exists()) {
            return PersistedCredentialsState()
        }
        legacyRunStateFile.inputStream().bufferedReader().use { reader ->
            val legacyRoot = configLoader.objectMapper().readTree(reader)
            val uploadedNode = legacyRoot.path("uploadedCredentials")
            if (uploadedNode == null || uploadedNode.isMissingNode || uploadedNode.isNull) {
                return PersistedCredentialsState()
            }
            return PersistedCredentialsState(
                uploadedCredentials = configLoader.objectMapper().treeToValue(
                    uploadedNode,
                    PersistedUploadedCredentials::class.java,
                ),
            )
        }
    }
}
