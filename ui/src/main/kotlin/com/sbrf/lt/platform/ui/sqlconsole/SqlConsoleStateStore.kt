package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class SqlConsoleStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
) {
    private val stateFile: Path = storageDir.resolve("sql-console-state.json")

    fun load(): PersistedSqlConsoleState {
        if (!stateFile.exists()) {
            return PersistedSqlConsoleState()
        }
        return try {
            stateFile.inputStream().bufferedReader().use {
                configLoader.objectMapper()
                    .readValue(it, PersistedSqlConsoleState::class.java)
                    .normalized()
            }
        } catch (_: Exception) {
            PersistedSqlConsoleState()
        }
    }

    fun save(state: PersistedSqlConsoleState) {
        storageDir.createDirectories()
        val normalized = state.normalized()
        val tempFile = storageDir.resolve("sql-console-state.json.tmp")
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
