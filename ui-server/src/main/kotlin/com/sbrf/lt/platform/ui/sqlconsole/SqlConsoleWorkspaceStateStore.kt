package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class SqlConsoleWorkspaceStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val legacyStateStore: LegacySqlConsoleStateStore = LegacySqlConsoleStateStore(storageDir, configLoader),
) {
    private val stateFile: Path = storageDir.resolve("sql-console-workspace-state.json")

    fun load(): PersistedSqlConsoleWorkspaceState {
        if (stateFile.exists()) {
            return try {
                stateFile.inputStream().bufferedReader().use {
                    configLoader.objectMapper()
                        .readValue(it, PersistedSqlConsoleWorkspaceState::class.java)
                        .normalized()
                }
            } catch (_: Exception) {
                PersistedSqlConsoleWorkspaceState()
            }
        }
        val migrated = legacyStateStore.load().toWorkspaceState()
        if (legacyStateStore.exists()) {
            save(migrated)
            cleanupLegacyCombinedSqlConsoleStateIfMigrated(storageDir, legacyStateStore)
        }
        return migrated
    }

    fun save(state: PersistedSqlConsoleWorkspaceState) {
        storageDir.createDirectories()
        val normalized = state.normalized()
        val tempFile = storageDir.resolve("sql-console-workspace-state.json.tmp")
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
