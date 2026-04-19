package com.sbrf.lt.platform.ui.sqlconsole

import java.nio.file.Path
import kotlin.io.path.exists

internal fun cleanupLegacyCombinedSqlConsoleStateIfMigrated(
    storageDir: Path,
    legacyStateStore: LegacySqlConsoleStateStore,
) {
    if (!legacyStateStore.exists()) {
        return
    }
    val workspaceStateExists = storageDir.resolve("sql-console-workspace-state.json").exists()
    val libraryStateExists = storageDir.resolve("sql-console-library-state.json").exists()
    val preferencesStateExists = storageDir.resolve("sql-console-preferences-state.json").exists()
    if (workspaceStateExists && libraryStateExists && preferencesStateExists) {
        legacyStateStore.delete()
    }
}
