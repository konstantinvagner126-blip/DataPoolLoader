package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Path
import kotlin.io.path.exists

class SqlConsoleLibraryStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val legacyStateStore: LegacySqlConsoleStateStore = LegacySqlConsoleStateStore(storageDir, configLoader),
) {
    private val stateFile: Path = storageDir.resolve(SQL_CONSOLE_LIBRARY_STATE_FILE_NAME)

    fun load(): PersistedSqlConsoleLibraryState {
        readOptionalSqlConsoleStateFile(
            stateFile = stateFile,
            configLoader = configLoader,
            stateClass = PersistedSqlConsoleLibraryState::class.java,
        )?.normalized()?.let { return it }

        normalizeLegacySplitPreferencesStateIfNeeded(storageDir, configLoader)
        readOptionalSqlConsoleStateFile(
            stateFile = stateFile,
            configLoader = configLoader,
            stateClass = PersistedSqlConsoleLibraryState::class.java,
        )?.normalized()?.let { return it }

        val migrated = legacyStateStore.load().toLibraryState()
        return migrateSqlConsoleStateIfNeeded(
            storageDir = storageDir,
            shouldMigrate = legacyStateStore.exists(),
            legacyStateStore = legacyStateStore,
            migratedState = migrated,
            save = ::save,
        )
    }

    fun save(state: PersistedSqlConsoleLibraryState) {
        saveSqlConsoleStateFile(
            storageDir = storageDir,
            stateFile = stateFile,
            configLoader = configLoader,
            state = state.normalized(),
        )
    }
}
