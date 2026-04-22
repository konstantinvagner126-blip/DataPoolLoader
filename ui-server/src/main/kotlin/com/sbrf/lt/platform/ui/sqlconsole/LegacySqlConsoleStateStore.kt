package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

class LegacySqlConsoleStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
) {
    private val stateFile: Path = storageDir.resolve("sql-console-state.json")

    fun exists(): Boolean = stateFile.exists()

    fun delete() {
        stateFile.deleteIfExists()
    }

    fun load(): LegacySqlConsoleState {
        return readOptionalSqlConsoleStateFile(
            stateFile = stateFile,
            configLoader = configLoader,
            stateClass = LegacySqlConsoleState::class.java,
        )?.normalized() ?: LegacySqlConsoleState()
    }
}
