package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

class LegacySqlConsoleStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
) {
    private val stateFile: Path = storageDir.resolve("sql-console-state.json")

    fun exists(): Boolean = stateFile.exists()

    fun load(): LegacySqlConsoleState {
        if (!stateFile.exists()) {
            return LegacySqlConsoleState()
        }
        return try {
            stateFile.inputStream().bufferedReader().use {
                configLoader.objectMapper()
                    .readValue(it, LegacySqlConsoleState::class.java)
                    .normalized()
            }
        } catch (_: Exception) {
            LegacySqlConsoleState()
        }
    }
}
