package com.sbrf.lt.platform.ui.sqlconsole

import java.nio.file.Path
import java.time.Clock

internal class SqlConsoleExecutionHistoryService(
    private val stateStore: SqlConsoleExecutionHistoryStateStore,
    private val workspaceRetentionService: SqlConsoleWorkspaceRetentionService,
    private val historyLimit: Int = DEFAULT_SQL_CONSOLE_EXECUTION_HISTORY_LIMIT,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val lock = Any()
    private val historiesByWorkspace: MutableMap<String, PersistedSqlConsoleExecutionHistoryState> = linkedMapOf()

    constructor(storageDir: Path) : this(
        stateStore = SqlConsoleExecutionHistoryStateStore(storageDir),
        workspaceRetentionService = SqlConsoleWorkspaceRetentionService(storageDir),
    )

    fun currentHistory(workspaceId: String? = null): PersistedSqlConsoleExecutionHistoryState = synchronized(lock) {
        val normalizedWorkspaceId = normalizeSqlConsoleWorkspaceId(workspaceId)
        touchWorkspaceHistory(
            workspaceId = normalizedWorkspaceId,
            historyState = currentWorkspaceHistory(normalizedWorkspaceId),
        )
    }

    fun recordExecutionSnapshot(execution: ActiveExecution) {
        synchronized(lock) {
            val nextEntry = PersistedSqlConsoleExecutionHistoryEntry(
                executionId = execution.snapshot.id,
                sql = execution.sql,
                selectedSourceNames = execution.selectedSourceNames,
                autoCommitEnabled = execution.snapshot.autoCommitEnabled,
                status = execution.snapshot.status.name,
                transactionState = execution.snapshot.transactionState.name,
                startedAt = execution.snapshot.startedAt,
                finishedAt = execution.snapshot.finishedAt,
                durationMillis = null,
                errorMessage = execution.snapshot.errorMessage,
            ).normalizedOrNull() ?: return
            val normalizedWorkspaceId = normalizeSqlConsoleWorkspaceId(execution.workspaceId)
            val currentHistory = currentWorkspaceHistory(normalizedWorkspaceId)
            val updatedHistory = PersistedSqlConsoleExecutionHistoryState(
                entries = listOf(nextEntry) + currentHistory.entries.filterNot { it.executionId == nextEntry.executionId },
            ).normalized(historyLimit)
            touchWorkspaceHistory(normalizedWorkspaceId, updatedHistory)
        }
    }

    private fun currentWorkspaceHistory(workspaceId: String?): PersistedSqlConsoleExecutionHistoryState {
        val normalizedWorkspaceId = normalizeSqlConsoleWorkspaceId(workspaceId)
        return historiesByWorkspace.getOrPut(normalizedWorkspaceId) {
            stateStore.load(normalizedWorkspaceId).normalized(historyLimit)
        }
    }

    private fun touchWorkspaceHistory(
        workspaceId: String,
        historyState: PersistedSqlConsoleExecutionHistoryState,
    ): PersistedSqlConsoleExecutionHistoryState {
        val touchedHistory = historyState.normalized(historyLimit).touched(clock.instant())
        historiesByWorkspace[workspaceId] = touchedHistory
        stateStore.save(workspaceId, touchedHistory)
        workspaceRetentionService.markWorkspaceAccessed(workspaceId)
        workspaceRetentionService.cleanupStaleWorkspaceFiles()
        return touchedHistory
    }
}
