package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.platform.ui.error.UiStateConflictException
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlConsoleQueryManagerTransactionSafetyTest {

    @Test
    fun `keeps pending transaction until commit`() {
        val manager = SqlConsoleQueryManager(sqlConsoleService = manualTransactionService())

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val pending = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, pending.status)
        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)

        val committed = manager.commit(started.id, OWNER_SESSION_ID, requireNotNull(started.ownerToken))

        assertEquals(SqlConsoleExecutionTransactionState.COMMITTED, committed.transactionState)
        assertTrue(committed.transactionShardNames.isEmpty())
        assertNull(committed.ownerToken)
        assertNull(committed.ownerLeaseExpiresAt)
        assertNull(committed.pendingCommitExpiresAt)

        val releaseError = assertFailsWith<UiStateConflictException> {
            manager.releaseOwnership(started.id, OWNER_SESSION_ID, requireNotNull(started.ownerToken))
        }
        assertTrue(releaseError.message!!.contains("control-path"))
    }

    @Test
    fun `clears control path metadata after explicit rollback`() {
        val manager = SqlConsoleQueryManager(sqlConsoleService = manualTransactionService())

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val pending = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, pending.status)
        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)

        val rolledBack = manager.rollback(started.id, OWNER_SESSION_ID, requireNotNull(started.ownerToken))

        assertEquals(SqlConsoleExecutionTransactionState.ROLLED_BACK, rolledBack.transactionState)
        assertTrue(rolledBack.transactionShardNames.isEmpty())
        assertNull(rolledBack.ownerToken)
        assertNull(rolledBack.ownerLeaseExpiresAt)
        assertNull(rolledBack.pendingCommitExpiresAt)
    }

    @Test
    fun `does not expose pending commit for read only script when auto commit disabled`() {
        val manager = SqlConsoleQueryManager(sqlConsoleService = manualTransactionService())

        val started = manager.startQuery(
            sql = "select 1 as id",
            credentialsPath = null,
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val finished = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, finished.status)
        assertEquals(SqlConsoleExecutionTransactionState.NONE, finished.transactionState)
        assertTrue(finished.transactionShardNames.isEmpty())
    }

    @Test
    fun `records execution history and updates same entry after commit`() {
        val historyService = SqlConsoleExecutionHistoryService(Files.createTempDirectory("sql-console-history-manager"))
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(),
            executionHistoryService = historyService,
        )

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            selectedSourceNames = listOf("db1"),
            workspaceId = "workspace-a",
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val pending = waitForCompletion(manager, started.id)
        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)

        val pendingHistory = historyService.currentHistory("workspace-a").entries.single()
        assertEquals("update demo set flag = true", pendingHistory.sql)
        assertEquals(listOf("db1"), pendingHistory.selectedSourceNames)
        assertEquals("PENDING_COMMIT", pendingHistory.transactionState)

        manager.commit(started.id, OWNER_SESSION_ID, requireNotNull(started.ownerToken))

        val committedHistory = historyService.currentHistory("workspace-a").entries.single()
        assertEquals("COMMITTED", committedHistory.transactionState)
        assertEquals("SUCCESS", committedHistory.status)
    }

    @Test
    fun `records execution history and updates same entry after rollback`() {
        val historyService = SqlConsoleExecutionHistoryService(Files.createTempDirectory("sql-console-history-manager"))
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(),
            executionHistoryService = historyService,
        )

        val started = manager.startQuery(
            sql = "update demo set flag = false",
            credentialsPath = null,
            selectedSourceNames = listOf("db1"),
            workspaceId = "workspace-a",
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val pending = waitForCompletion(manager, started.id)
        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)

        val pendingHistory = historyService.currentHistory("workspace-a").entries.single()
        assertEquals(started.id, pendingHistory.executionId)
        assertEquals("PENDING_COMMIT", pendingHistory.transactionState)

        manager.rollback(started.id, OWNER_SESSION_ID, requireNotNull(started.ownerToken))

        val rolledBackHistory = historyService.currentHistory("workspace-a").entries.single()
        assertEquals(started.id, rolledBackHistory.executionId)
        assertEquals("ROLLED_BACK", rolledBackHistory.transactionState)
        assertEquals("SUCCESS", rolledBackHistory.status)
    }

    @Test
    fun `rejects second manual transaction while another manual transaction is running`() {
        val releaseExecution = CountDownLatch(1)
        val manager = SqlConsoleQueryManager(sqlConsoleService = manualTransactionService(releaseExecution))

        manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )

        val error = assertFailsWith<UiStateConflictException> {
            manager.startQuery(
                sql = "update demo set flag = false",
                credentialsPath = null,
                ownerSessionId = "owner-session-2",
                transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
            )
        }

        releaseExecution.countDown()
        assertTrue(error.message!!.contains("ручная транзакция"))
    }

    @Test
    fun `rejects second manual transaction when another tab already has pending commit`() {
        val manager = SqlConsoleQueryManager(sqlConsoleService = manualTransactionService())

        val firstStarted = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val pending = waitForCompletion(manager, firstStarted.id)
        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)

        val error = assertFailsWith<UiStateConflictException> {
            manager.startQuery(
                sql = "update demo set flag = false",
                credentialsPath = null,
                ownerSessionId = "owner-session-2",
                transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
            )
        }

        assertEquals(
            "В другой вкладке SQL-консоли есть незавершенная транзакция. Сначала выполните Commit или Rollback в той вкладке. Пока транзакция не завершена, запуск новой ручной транзакции недоступен.",
            error.message,
        )
    }

    @Test
    fun `rejects commit from another owner session`() {
        val manager = SqlConsoleQueryManager(sqlConsoleService = manualTransactionService())

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val pending = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)

        val error = assertFailsWith<UiStateConflictException> {
            manager.commit(started.id, "other-owner", requireNotNull(started.ownerToken))
        }
        assertTrue(error.message!!.contains("не принадлежит"))
    }

    @Test
    fun `heartbeat rotates owner token and fences stale token`() {
        val manager = SqlConsoleQueryManager(sqlConsoleService = sqlConsoleQueryManagerSuccessService())

        val started = manager.startQuery(
            sql = "select 1 as id",
            credentialsPath = null,
            ownerSessionId = OWNER_SESSION_ID,
        )
        val initialToken = requireNotNull(started.ownerToken)

        val rotated = manager.heartbeat(started.id, OWNER_SESSION_ID, initialToken)
        val rotatedToken = requireNotNull(rotated.ownerToken)

        assertTrue(rotatedToken != initialToken)

        val error = assertFailsWith<UiStateConflictException> {
            manager.cancel(started.id, OWNER_SESSION_ID, initialToken)
        }
        assertTrue(error.message!!.contains("не принадлежит"))
    }

    @Test
    fun `release pending commit requires heartbeat recovery before commit`() {
        val manager = SqlConsoleQueryManager(sqlConsoleService = manualTransactionService())

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val initialToken = requireNotNull(started.ownerToken)
        val pending = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)

        val released = manager.releaseOwnership(started.id, OWNER_SESSION_ID, initialToken)
        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, released.transactionState)

        val releasedCommitError = assertFailsWith<UiStateConflictException> {
            manager.commit(started.id, OWNER_SESSION_ID, initialToken)
        }
        assertTrue(releasedCommitError.message!!.contains("control-path"))

        val recovered = manager.heartbeat(started.id, OWNER_SESSION_ID, initialToken)
        val recoveredToken = requireNotNull(recovered.ownerToken)
        val committed = manager.commit(started.id, OWNER_SESSION_ID, recoveredToken)

        assertEquals(SqlConsoleExecutionTransactionState.COMMITTED, committed.transactionState)
    }

    @Test
    fun `release pending commit auto rollbacks when recovery window expires`() {
        var now = Instant.parse("2026-04-23T00:00:00Z")
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(),
            clock = { now },
            ownerReleaseRecoveryWindow = Duration.ofSeconds(3),
        )

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val initialToken = requireNotNull(started.ownerToken)
        val pending = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)

        manager.releaseOwnership(started.id, OWNER_SESSION_ID, initialToken)
        now = now.plusSeconds(4)
        manager.enforceSafetyTimeouts()
        val rolledBack = manager.snapshot(started.id)

        assertEquals(SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_OWNER_LOSS, rolledBack.transactionState)
        assertTrue(rolledBack.errorMessage!!.contains("владелец"))
        assertFinalControlPathCleared(rolledBack)
    }

    @Test
    fun `records execution history after released pending commit owner loss rollback`() {
        var now = Instant.parse("2026-04-23T00:00:00Z")
        val historyService = SqlConsoleExecutionHistoryService(Files.createTempDirectory("sql-console-history-owner-loss"))
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(),
            executionHistoryService = historyService,
            clock = { now },
            ownerReleaseRecoveryWindow = Duration.ofSeconds(3),
        )

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            selectedSourceNames = listOf("db1"),
            workspaceId = "workspace-owner-loss",
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val initialToken = requireNotNull(started.ownerToken)
        val pending = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)
        assertSingleHistoryState(
            historyService = historyService,
            workspaceId = "workspace-owner-loss",
            executionId = started.id,
            transactionState = "PENDING_COMMIT",
        )

        manager.releaseOwnership(started.id, OWNER_SESSION_ID, initialToken)
        now = now.plusSeconds(4)
        manager.enforceSafetyTimeouts()

        assertSingleHistoryState(
            historyService = historyService,
            workspaceId = "workspace-owner-loss",
            executionId = started.id,
            transactionState = "ROLLED_BACK_BY_OWNER_LOSS",
        )
    }

    @Test
    fun `auto rollbacks pending commit when ttl expires`() {
        var now = Instant.parse("2026-04-23T00:00:00Z")
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(),
            clock = { now },
            pendingCommitTtl = Duration.ofSeconds(5),
        )

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val pending = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)
        now = now.plusSeconds(6)

        manager.enforceSafetyTimeouts()
        val timedOut = manager.snapshot(started.id)

        assertEquals(SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_TIMEOUT, timedOut.transactionState)
        assertTrue(timedOut.errorMessage!!.contains("TTL"))
        assertFinalControlPathCleared(timedOut)
    }

    @Test
    fun `records execution history after pending commit ttl rollback`() {
        var now = Instant.parse("2026-04-23T00:00:00Z")
        val historyService = SqlConsoleExecutionHistoryService(Files.createTempDirectory("sql-console-history-ttl"))
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(),
            executionHistoryService = historyService,
            clock = { now },
            pendingCommitTtl = Duration.ofSeconds(5),
        )

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            selectedSourceNames = listOf("db1"),
            workspaceId = "workspace-ttl",
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        val pending = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)
        assertSingleHistoryState(
            historyService = historyService,
            workspaceId = "workspace-ttl",
            executionId = started.id,
            transactionState = "PENDING_COMMIT",
        )

        now = now.plusSeconds(6)
        manager.enforceSafetyTimeouts()

        assertSingleHistoryState(
            historyService = historyService,
            workspaceId = "workspace-ttl",
            executionId = started.id,
            transactionState = "ROLLED_BACK_BY_TIMEOUT",
        )
    }

    @Test
    fun `auto rollbacks completed manual transaction when owner lease was lost during running`() {
        var now = Instant.parse("2026-04-23T00:00:00Z")
        val releaseExecution = CountDownLatch(1)
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(releaseExecution),
            clock = { now },
            ownerLeaseDuration = Duration.ofSeconds(5),
        )

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )

        now = now.plusSeconds(6)
        manager.enforceSafetyTimeouts()
        releaseExecution.countDown()
        val finished = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_OWNER_LOSS, finished.transactionState)
        assertTrue(finished.errorMessage!!.contains("владелец"))
        assertFinalControlPathCleared(finished)
    }

    @Test
    fun `records execution history after owner lease was lost during running`() {
        var now = Instant.parse("2026-04-23T00:00:00Z")
        val releaseExecution = CountDownLatch(1)
        val historyService = SqlConsoleExecutionHistoryService(Files.createTempDirectory("sql-console-history-running-owner-loss"))
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(releaseExecution),
            executionHistoryService = historyService,
            clock = { now },
            ownerLeaseDuration = Duration.ofSeconds(5),
        )

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            selectedSourceNames = listOf("db1"),
            workspaceId = "workspace-running-owner-loss",
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )

        now = now.plusSeconds(6)
        manager.enforceSafetyTimeouts()
        releaseExecution.countDown()
        val finished = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_OWNER_LOSS, finished.transactionState)
        assertSingleHistoryState(
            historyService = historyService,
            workspaceId = "workspace-running-owner-loss",
            executionId = started.id,
            transactionState = "ROLLED_BACK_BY_OWNER_LOSS",
        )
    }

    @Test
    fun `release running manual transaction causes safe rollback after recovery window`() {
        var now = Instant.parse("2026-04-23T00:00:00Z")
        val releaseExecution = CountDownLatch(1)
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(releaseExecution),
            clock = { now },
            ownerReleaseRecoveryWindow = Duration.ofSeconds(3),
        )

        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )

        manager.releaseOwnership(started.id, OWNER_SESSION_ID, requireNotNull(started.ownerToken))
        now = now.plusSeconds(4)
        manager.enforceSafetyTimeouts()
        releaseExecution.countDown()
        val finished = waitForCompletion(manager, started.id)

        assertEquals(SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_OWNER_LOSS, finished.transactionState)
        assertTrue(finished.errorMessage!!.contains("владелец"))
        assertFinalControlPathCleared(finished)
    }

    @Test
    fun `releasing one workspace execution does not affect another workspace execution`() {
        val releaseExecution = CountDownLatch(1)
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = autoCommitBlockingService(releaseExecution),
        )

        val first = manager.startQuery(
            sql = "select 1 as first_value",
            credentialsPath = null,
            workspaceId = "workspace-a",
            ownerSessionId = "owner-a",
        )
        val second = manager.startQuery(
            sql = "select 1 as second_value",
            credentialsPath = null,
            workspaceId = "workspace-b",
            ownerSessionId = "owner-b",
        )

        assertEquals(first.id, manager.currentSnapshot("workspace-a")?.id)
        assertEquals(second.id, manager.currentSnapshot("workspace-b")?.id)

        manager.releaseOwnership(first.id, "owner-a", requireNotNull(first.ownerToken))
        val cancelledSecond = manager.cancel(second.id, "owner-b", requireNotNull(second.ownerToken))

        assertTrue(cancelledSecond.cancelRequested)

        releaseExecution.countDown()
        val finishedFirst = waitForCompletion(manager, first.id)
        val finishedSecond = waitForCompletion(manager, second.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, finishedFirst.status)
        assertEquals(SqlConsoleExecutionStatus.CANCELLED, finishedSecond.status)
    }

    @Test
    fun `owner loss in one workspace does not remove control path from another workspace`() {
        var now = Instant.parse("2026-04-23T00:00:00Z")
        val releaseExecution = CountDownLatch(1)
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = autoCommitBlockingService(releaseExecution),
            clock = { now },
            ownerLeaseDuration = Duration.ofSeconds(30),
            ownerReleaseRecoveryWindow = Duration.ofSeconds(3),
        )

        val first = manager.startQuery(
            sql = "select 1 as first_value",
            credentialsPath = null,
            workspaceId = "workspace-a",
            ownerSessionId = "owner-a",
        )
        val second = manager.startQuery(
            sql = "select 1 as second_value",
            credentialsPath = null,
            workspaceId = "workspace-b",
            ownerSessionId = "owner-b",
        )

        manager.releaseOwnership(first.id, "owner-a", requireNotNull(first.ownerToken))
        now = now.plusSeconds(4)
        manager.enforceSafetyTimeouts()

        val lostOwnerError = assertFailsWith<UiStateConflictException> {
            manager.heartbeat(first.id, "owner-a", requireNotNull(first.ownerToken))
        }
        assertTrue(lostOwnerError.message!!.contains("потеряла владельца"))

        val cancelledSecond = manager.cancel(second.id, "owner-b", requireNotNull(second.ownerToken))
        assertTrue(cancelledSecond.cancelRequested)
        assertEquals(first.id, manager.currentSnapshot("workspace-a")?.id)
        assertEquals(second.id, manager.currentSnapshot("workspace-b")?.id)

        releaseExecution.countDown()
        val finishedFirst = waitForCompletion(manager, first.id)
        val finishedSecond = waitForCompletion(manager, second.id)

        assertEquals(SqlConsoleExecutionStatus.SUCCESS, finishedFirst.status)
        assertEquals(SqlConsoleExecutionStatus.CANCELLED, finishedSecond.status)
    }

    private fun assertFinalControlPathCleared(snapshot: SqlConsoleExecutionSnapshot) {
        assertNull(snapshot.ownerToken)
        assertNull(snapshot.ownerLeaseExpiresAt)
        assertNull(snapshot.pendingCommitExpiresAt)
    }

    private fun assertSingleHistoryState(
        historyService: SqlConsoleExecutionHistoryService,
        workspaceId: String,
        executionId: String,
        transactionState: String,
    ) {
        val entries = historyService.currentHistory(workspaceId).entries
        assertEquals(1, entries.size)
        assertEquals(executionId, entries.single().executionId)
        assertEquals(transactionState, entries.single().transactionState)
    }
}
