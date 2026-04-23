package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Path

internal class SqlConsoleExecutionHistoryStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
) {
    private val defaultStateFile: Path = storageDir.resolve("sql-console-execution-history-state.json")

    fun load(workspaceId: String? = null): PersistedSqlConsoleExecutionHistoryState =
        readOptionalSqlConsoleStateFile(
            stateFile = resolveWorkspaceStateFile(normalizeSqlConsoleWorkspaceId(workspaceId)),
            configLoader = configLoader,
            stateClass = PersistedSqlConsoleExecutionHistoryState::class.java,
        )?.normalized() ?: PersistedSqlConsoleExecutionHistoryState()

    fun save(
        workspaceId: String? = null,
        state: PersistedSqlConsoleExecutionHistoryState,
    ) {
        saveSqlConsoleStateFile(
            storageDir = storageDir,
            stateFile = resolveWorkspaceStateFile(normalizeSqlConsoleWorkspaceId(workspaceId)),
            configLoader = configLoader,
            state = state.normalized(),
        )
    }

    private fun resolveWorkspaceStateFile(workspaceId: String): Path =
        if (workspaceId == DEFAULT_SQL_CONSOLE_WORKSPACE_ID) {
            defaultStateFile
        } else {
            storageDir.resolve("sql-console-execution-history-state-${workspaceId.toFileNameToken()}.json")
        }
}
