package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

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
        readOptionalRunStateFile(stateFile, configLoader, PersistedCredentialsState::class.java)?.let {
            return it
        }
        val migratedState = loadLegacyState()
        if (migratedState.uploadedCredentials != null) {
            save(migratedState)
            clearLegacyUploadedCredentials()
        }
        return migratedState
    }

    fun save(state: PersistedCredentialsState) {
        saveRunStateFile(storageDir, stateFile, configLoader, state)
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

    private fun clearLegacyUploadedCredentials() {
        if (!legacyRunStateFile.exists()) {
            return
        }
        val mapper = configLoader.objectMapper()
        legacyRunStateFile.inputStream().bufferedReader().use { reader ->
            val root = mapper.readTree(reader)
            if (!root.isObject) {
                return
            }
            val rootObject = root.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
            if (!rootObject.has("uploadedCredentials")) {
                return
            }
            rootObject.remove("uploadedCredentials")
            saveRunStateFile(storageDir, legacyRunStateFile, configLoader, rootObject)
        }
    }
}
