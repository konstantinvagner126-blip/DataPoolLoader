package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Path

class SqlConsoleWorkspaceStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val legacyStateStore: LegacySqlConsoleStateStore = LegacySqlConsoleStateStore(storageDir, configLoader),
) {
    private val defaultStateFile: Path = storageDir.resolve("sql-console-workspace-state.json")

    fun load(workspaceId: String? = null): PersistedSqlConsoleWorkspaceState {
        val normalizedWorkspaceId = normalizeSqlConsoleWorkspaceId(workspaceId)
        val stateFile = resolveWorkspaceStateFile(normalizedWorkspaceId)
        readOptionalSqlConsoleStateFile(
            stateFile = stateFile,
            configLoader = configLoader,
            stateClass = PersistedSqlConsoleWorkspaceState::class.java,
        )?.normalized()?.let { return it }

        if (normalizedWorkspaceId != DEFAULT_SQL_CONSOLE_WORKSPACE_ID) {
            return PersistedSqlConsoleWorkspaceState().normalized()
        }

        val migrated = legacyStateStore.load().toWorkspaceState()
        return migrateSqlConsoleStateIfNeeded(
            storageDir = storageDir,
            shouldMigrate = normalizedWorkspaceId == DEFAULT_SQL_CONSOLE_WORKSPACE_ID && legacyStateStore.exists(),
            legacyStateStore = legacyStateStore,
            migratedState = migrated,
            save = { save(normalizedWorkspaceId, it) },
        )
    }

    fun save(workspaceId: String? = null, state: PersistedSqlConsoleWorkspaceState) {
        val stateFile = resolveWorkspaceStateFile(normalizeSqlConsoleWorkspaceId(workspaceId))
        saveSqlConsoleStateFile(
            storageDir = storageDir,
            stateFile = stateFile,
            configLoader = configLoader,
            state = state.normalized(),
        )
    }

    private fun resolveWorkspaceStateFile(workspaceId: String): Path =
        if (workspaceId == DEFAULT_SQL_CONSOLE_WORKSPACE_ID) {
            defaultStateFile
        } else {
            storageDir.resolve("sql-console-workspace-state-${workspaceId.toFileNameToken()}.json")
        }
}

internal const val DEFAULT_SQL_CONSOLE_WORKSPACE_ID = "default"

internal fun normalizeSqlConsoleWorkspaceId(value: String?): String =
    value?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_SQL_CONSOLE_WORKSPACE_ID

internal fun String.toFileNameToken(): String =
    lowercase()
        .map { char ->
            when {
                char.isLetterOrDigit() -> char
                char == '-' -> char
                else -> '_'
            }
        }
        .joinToString("")
        .take(80)
        .ifBlank { DEFAULT_SQL_CONSOLE_WORKSPACE_ID }
