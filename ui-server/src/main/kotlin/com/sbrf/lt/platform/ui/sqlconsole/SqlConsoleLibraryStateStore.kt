package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Path
import kotlin.io.path.exists

class SqlConsoleLibraryStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val legacyStateStore: LegacySqlConsoleStateStore = LegacySqlConsoleStateStore(storageDir, configLoader),
) {
    private val stateFile: Path = storageDir.resolve("sql-console-library-state.json")
    private val legacyPreferencesFile: Path = storageDir.resolve("sql-console-preferences-state.json")

    fun load(): PersistedSqlConsoleLibraryState {
        readOptionalSqlConsoleStateFile(
            stateFile = stateFile,
            configLoader = configLoader,
            stateClass = PersistedSqlConsoleLibraryState::class.java,
        )?.normalized()?.let { return it }

        val migrated = loadLegacyPreferencesState()
            ?: legacyStateStore.load().toLibraryState()
        return migrateSqlConsoleStateIfNeeded(
            storageDir = storageDir,
            shouldMigrate = legacyPreferencesFile.exists() || legacyStateStore.exists(),
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

    private fun loadLegacyPreferencesState(): PersistedSqlConsoleLibraryState? {
        return readOptionalSqlConsoleStateFile(
            stateFile = legacyPreferencesFile,
            configLoader = configLoader,
            stateClass = LegacySqlConsoleState::class.java,
        )?.toLibraryState()
    }
}
