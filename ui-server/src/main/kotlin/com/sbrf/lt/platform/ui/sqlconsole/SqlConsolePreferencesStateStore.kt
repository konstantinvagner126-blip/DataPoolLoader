package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Path

class SqlConsolePreferencesStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val legacyStateStore: LegacySqlConsoleStateStore = LegacySqlConsoleStateStore(storageDir, configLoader),
) {
    private val stateFile: Path = storageDir.resolve(SQL_CONSOLE_PREFERENCES_STATE_FILE_NAME)

    fun load(): PersistedSqlConsolePreferencesState {
        normalizeLegacySplitPreferencesStateIfNeeded(storageDir, configLoader)
        readOptionalSqlConsoleStateFile(
            stateFile = stateFile,
            configLoader = configLoader,
            stateClass = PersistedSqlConsolePreferencesState::class.java,
        )?.normalized()?.let {
            cleanupLegacyCombinedSqlConsoleStateIfMigrated(storageDir, legacyStateStore)
            return it
        }

        val migrated = legacyStateStore.load().toPreferencesState()
        return migrateSqlConsoleStateIfNeeded(
            storageDir = storageDir,
            shouldMigrate = legacyStateStore.exists(),
            legacyStateStore = legacyStateStore,
            migratedState = migrated,
            save = ::save,
        )
    }

    fun save(state: PersistedSqlConsolePreferencesState) {
        saveSqlConsoleStateFile(
            storageDir = storageDir,
            stateFile = stateFile,
            configLoader = configLoader,
            state = state.normalized(),
        )
    }
}
