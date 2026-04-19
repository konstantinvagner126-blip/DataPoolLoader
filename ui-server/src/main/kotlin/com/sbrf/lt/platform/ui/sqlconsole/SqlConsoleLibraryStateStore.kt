package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class SqlConsoleLibraryStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val legacyStateStore: LegacySqlConsoleStateStore = LegacySqlConsoleStateStore(storageDir, configLoader),
) {
    private val stateFile: Path = storageDir.resolve("sql-console-library-state.json")
    private val legacyPreferencesFile: Path = storageDir.resolve("sql-console-preferences-state.json")

    fun load(): PersistedSqlConsoleLibraryState {
        if (stateFile.exists()) {
            return try {
                stateFile.inputStream().bufferedReader().use {
                    configLoader.objectMapper()
                        .readValue(it, PersistedSqlConsoleLibraryState::class.java)
                        .normalized()
                }
            } catch (_: Exception) {
                PersistedSqlConsoleLibraryState()
            }
        }
        val migrated = loadLegacyPreferencesState()
            ?: legacyStateStore.load().toLibraryState()
        if (legacyPreferencesFile.exists() || legacyStateStore.exists()) {
            save(migrated)
            cleanupLegacyCombinedSqlConsoleStateIfMigrated(storageDir, legacyStateStore)
        }
        return migrated
    }

    fun save(state: PersistedSqlConsoleLibraryState) {
        storageDir.createDirectories()
        val normalized = state.normalized()
        val tempFile = storageDir.resolve("sql-console-library-state.json.tmp")
        tempFile.outputStream().bufferedWriter().use {
            configLoader.objectMapper().writerWithDefaultPrettyPrinter().writeValue(it, normalized)
        }
        Files.move(
            tempFile,
            stateFile,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private fun loadLegacyPreferencesState(): PersistedSqlConsoleLibraryState? {
        if (!legacyPreferencesFile.exists()) {
            return null
        }
        return try {
            legacyPreferencesFile.inputStream().bufferedReader().use {
                configLoader.objectMapper()
                    .readValue(it, LegacySqlConsoleState::class.java)
                    .toLibraryState()
            }
        } catch (_: Exception) {
            null
        }
    }
}
