package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class SqlConsolePreferencesStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val legacyStateStore: LegacySqlConsoleStateStore = LegacySqlConsoleStateStore(storageDir, configLoader),
) {
    private val stateFile: Path = storageDir.resolve("sql-console-preferences-state.json")

    fun load(): PersistedSqlConsolePreferencesState {
        if (stateFile.exists()) {
            return try {
                stateFile.inputStream().bufferedReader().use {
                    configLoader.objectMapper()
                        .readValue(it, PersistedSqlConsolePreferencesState::class.java)
                        .normalized()
                }
            } catch (_: Exception) {
                PersistedSqlConsolePreferencesState()
            }
        }
        val migrated = legacyStateStore.load().toPreferencesState()
        if (legacyStateStore.exists()) {
            save(migrated)
        }
        return migrated
    }

    fun save(state: PersistedSqlConsolePreferencesState) {
        storageDir.createDirectories()
        val normalized = state.normalized()
        val tempFile = storageDir.resolve("sql-console-preferences-state.json.tmp")
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
}
