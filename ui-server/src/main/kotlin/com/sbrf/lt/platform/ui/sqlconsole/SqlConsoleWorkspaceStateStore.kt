package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Path

class SqlConsoleWorkspaceStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val legacyStateStore: LegacySqlConsoleStateStore = LegacySqlConsoleStateStore(storageDir, configLoader),
) {
    private val stateFile: Path = storageDir.resolve("sql-console-workspace-state.json")

    fun load(): PersistedSqlConsoleWorkspaceState {
        readOptionalSqlConsoleStateFile(
            stateFile = stateFile,
            configLoader = configLoader,
            stateClass = PersistedSqlConsoleWorkspaceState::class.java,
        )?.normalized()?.let { return it }

        val migrated = legacyStateStore.load().toWorkspaceState()
        return migrateSqlConsoleStateIfNeeded(
            storageDir = storageDir,
            shouldMigrate = legacyStateStore.exists(),
            legacyStateStore = legacyStateStore,
            migratedState = migrated,
            save = ::save,
        )
    }

    fun save(state: PersistedSqlConsoleWorkspaceState) {
        saveSqlConsoleStateFile(
            storageDir = storageDir,
            stateFile = stateFile,
            configLoader = configLoader,
            state = state.normalized(),
        )
    }
}
