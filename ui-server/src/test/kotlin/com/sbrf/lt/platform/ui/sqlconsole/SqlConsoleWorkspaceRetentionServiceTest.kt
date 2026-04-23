package com.sbrf.lt.platform.ui.sqlconsole

import java.nio.file.Files
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqlConsoleWorkspaceRetentionServiceTest {

    @Test
    fun `cleanup removes stale non-default workspace files and keeps default plus recent ones`() {
        val storageDir = Files.createTempDirectory("sql-console-workspace-retention")
        val now = Instant.parse("2026-04-23T12:00:00Z")
        val workspaceStore = SqlConsoleWorkspaceStateStore(storageDir)
        val historyStore = SqlConsoleExecutionHistoryStateStore(storageDir)

        workspaceStore.save(
            DEFAULT_SQL_CONSOLE_WORKSPACE_ID,
            PersistedSqlConsoleWorkspaceState(
                draftSql = "select * from default_workspace",
                lastAccessedAt = now.minus(Duration.ofDays(90)),
            ),
        )
        workspaceStore.save(
            "workspace-old",
            PersistedSqlConsoleWorkspaceState(
                draftSql = "select * from old_workspace",
                lastAccessedAt = now.minus(Duration.ofDays(45)),
            ),
        )
        historyStore.save(
            "workspace-old",
            PersistedSqlConsoleExecutionHistoryState(
                entries = listOf(
                    PersistedSqlConsoleExecutionHistoryEntry(
                        executionId = "old-exec",
                        sql = "select * from old_workspace",
                        status = "SUCCESS",
                        startedAt = now.minus(Duration.ofDays(45)),
                        finishedAt = now.minus(Duration.ofDays(45)).plusSeconds(2),
                    ),
                ),
                lastAccessedAt = now.minus(Duration.ofDays(45)),
            ),
        )
        workspaceStore.save(
            "workspace-recent",
            PersistedSqlConsoleWorkspaceState(
                draftSql = "select * from recent_workspace",
                lastAccessedAt = now.minus(Duration.ofDays(5)),
            ),
        )

        SqlConsoleWorkspaceRetentionService(
            storageDir = storageDir,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            retention = Duration.ofDays(30),
        ).cleanupStaleWorkspaceFiles()

        assertTrue(storageDir.resolve(SQL_CONSOLE_WORKSPACE_STATE_DEFAULT_FILE_NAME).exists())
        assertTrue(storageDir.resolve("${SQL_CONSOLE_WORKSPACE_STATE_FILE_PREFIX}${workspaceRetentionToken("workspace-recent")}.json").exists())
        assertTrue(storageDir.resolve("${SQL_CONSOLE_WORKSPACE_STATE_FILE_PREFIX}${workspaceRetentionToken("workspace-old")}.json").notExists())
        assertTrue(storageDir.resolve("${SQL_CONSOLE_EXECUTION_HISTORY_STATE_FILE_PREFIX}${workspaceRetentionToken("workspace-old")}.json").notExists())
    }

    @Test
    fun `active workspace stays pinned while stale sibling workspace is cleaned up`() {
        val storageDir = Files.createTempDirectory("sql-console-workspace-pin")
        val now = Instant.parse("2026-04-23T12:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val retentionService = SqlConsoleWorkspaceRetentionService(
            storageDir = storageDir,
            clock = clock,
            retention = Duration.ofDays(30),
        )
        val workspaceStore = SqlConsoleWorkspaceStateStore(storageDir)
        val historyStore = SqlConsoleExecutionHistoryStateStore(storageDir)

        workspaceStore.save(
            "workspace-active",
            PersistedSqlConsoleWorkspaceState(
                draftSql = "select * from pinned_workspace",
                lastAccessedAt = now.minus(Duration.ofDays(45)),
            ),
        )
        historyStore.save(
            "workspace-active",
            PersistedSqlConsoleExecutionHistoryState(
                entries = listOf(
                    PersistedSqlConsoleExecutionHistoryEntry(
                        executionId = "active-exec",
                        sql = "select * from pinned_workspace",
                        status = "SUCCESS",
                        startedAt = now.minus(Duration.ofDays(45)),
                        finishedAt = now.minus(Duration.ofDays(45)).plusSeconds(1),
                    ),
                ),
                lastAccessedAt = now.minus(Duration.ofDays(45)),
            ),
        )
        workspaceStore.save(
            "workspace-stale",
            PersistedSqlConsoleWorkspaceState(
                draftSql = "select * from stale_workspace",
                lastAccessedAt = now.minus(Duration.ofDays(45)),
            ),
        )
        historyStore.save(
            "workspace-stale",
            PersistedSqlConsoleExecutionHistoryState(
                entries = listOf(
                    PersistedSqlConsoleExecutionHistoryEntry(
                        executionId = "stale-exec",
                        sql = "select * from stale_workspace",
                        status = "SUCCESS",
                        startedAt = now.minus(Duration.ofDays(45)),
                        finishedAt = now.minus(Duration.ofDays(45)).plusSeconds(1),
                    ),
                ),
                lastAccessedAt = now.minus(Duration.ofDays(45)),
            ),
        )

        val stateService = SqlConsoleStateService(
            workspaceStore = workspaceStore,
            libraryStore = SqlConsoleLibraryStateStore(storageDir),
            preferencesStore = SqlConsolePreferencesStateStore(storageDir),
            workspaceRetentionService = retentionService,
            clock = clock,
        )

        val response = stateService.currentState("workspace-active")

        assertEquals("select * from pinned_workspace", response.draftSql)
        assertTrue(storageDir.resolve("${SQL_CONSOLE_WORKSPACE_STATE_FILE_PREFIX}${workspaceRetentionToken("workspace-active")}.json").exists())
        assertTrue(storageDir.resolve("${SQL_CONSOLE_EXECUTION_HISTORY_STATE_FILE_PREFIX}${workspaceRetentionToken("workspace-active")}.json").exists())
        assertTrue(storageDir.resolve("${SQL_CONSOLE_WORKSPACE_STATE_FILE_PREFIX}${workspaceRetentionToken("workspace-stale")}.json").notExists())
        assertTrue(storageDir.resolve("${SQL_CONSOLE_EXECUTION_HISTORY_STATE_FILE_PREFIX}${workspaceRetentionToken("workspace-stale")}.json").notExists())
    }

    @Test
    fun `recent workspace survives cleanup and reopens with preserved draft`() {
        val storageDir = Files.createTempDirectory("sql-console-workspace-reopen")
        val now = Instant.parse("2026-04-23T12:00:00Z")
        val workspaceStore = SqlConsoleWorkspaceStateStore(storageDir)
        workspaceStore.save(
            "workspace-recent",
            PersistedSqlConsoleWorkspaceState(
                draftSql = "select * from reopen_workspace",
                lastAccessedAt = now.minus(Duration.ofDays(2)),
            ),
        )

        SqlConsoleWorkspaceRetentionService(
            storageDir = storageDir,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            retention = Duration.ofDays(30),
        ).cleanupStaleWorkspaceFiles()

        val reopened = SqlConsoleStateService(storageDir).currentState("workspace-recent")

        assertEquals("select * from reopen_workspace", reopened.draftSql)
        assertFalse(storageDir.resolve("${SQL_CONSOLE_WORKSPACE_STATE_FILE_PREFIX}${workspaceRetentionToken("workspace-recent")}.json").notExists())
    }
}
