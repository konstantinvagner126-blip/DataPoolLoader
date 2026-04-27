package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.platform.ui.error.UiStateConflictException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlConsoleQueryManagerCurrentSnapshotTest {

    @Test
    fun `current snapshot exposes ttl rollback terminal state without control path metadata`() {
        var now = Instant.parse("2026-04-27T00:00:00Z")
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(),
            clock = { now },
            pendingCommitTtl = Duration.ofSeconds(5),
        )
        val started = startPendingManualTransaction(
            manager = manager,
            workspaceId = "workspace-ttl-current",
        )

        val pendingCurrent = assertNotNull(manager.currentSnapshot("workspace-ttl-current"))
        assertEquals(started.id, pendingCurrent.id)
        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pendingCurrent.transactionState)
        assertNotNull(pendingCurrent.ownerToken)

        now = now.plusSeconds(6)
        manager.enforceSafetyTimeouts()

        val terminalCurrent = assertNotNull(manager.currentSnapshot("workspace-ttl-current"))
        assertEquals(started.id, terminalCurrent.id)
        assertEquals(SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_TIMEOUT, terminalCurrent.transactionState)
        assertTrue(terminalCurrent.transactionShardNames.isEmpty())
        assertTrue(terminalCurrent.errorMessage!!.contains("TTL"))
        assertFinalControlPathCleared(terminalCurrent)
        assertNull(manager.currentSnapshot("workspace-other"))
    }

    @Test
    fun `current snapshot exposes owner-loss rollback terminal state without control path metadata`() {
        var now = Instant.parse("2026-04-27T00:00:00Z")
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = manualTransactionService(),
            clock = { now },
            ownerReleaseRecoveryWindow = Duration.ofSeconds(3),
        )
        val started = startPendingManualTransaction(
            manager = manager,
            workspaceId = "workspace-owner-loss-current",
        )
        val ownerToken = requireNotNull(started.ownerToken)

        manager.releaseOwnership(started.id, OWNER_SESSION_ID, ownerToken)
        now = now.plusSeconds(4)
        manager.enforceSafetyTimeouts()

        val terminalCurrent = assertNotNull(manager.currentSnapshot("workspace-owner-loss-current"))
        assertEquals(started.id, terminalCurrent.id)
        assertEquals(SqlConsoleExecutionTransactionState.ROLLED_BACK_BY_OWNER_LOSS, terminalCurrent.transactionState)
        assertTrue(terminalCurrent.transactionShardNames.isEmpty())
        assertTrue(terminalCurrent.errorMessage!!.contains("владелец"))
        assertFinalControlPathCleared(terminalCurrent)
    }

    @Test
    fun `current snapshot clears control path after owner lease is lost while running`() {
        var now = Instant.parse("2026-04-27T00:00:00Z")
        val releaseExecution = CountDownLatch(1)
        val manager = SqlConsoleQueryManager(
            sqlConsoleService = autoCommitBlockingService(releaseExecution),
            clock = { now },
            ownerLeaseDuration = Duration.ofSeconds(5),
        )
        val started = manager.startQuery(
            sql = "select pg_sleep(10)",
            credentialsPath = null,
            selectedSourceNames = listOf("db1"),
            workspaceId = "workspace-running-owner-loss-current",
            ownerSessionId = OWNER_SESSION_ID,
        )
        val ownerToken = requireNotNull(started.ownerToken)

        val runningCurrent = assertNotNull(manager.currentSnapshot("workspace-running-owner-loss-current"))
        assertEquals(started.id, runningCurrent.id)
        assertEquals(SqlConsoleExecutionStatus.RUNNING, runningCurrent.status)
        assertNotNull(runningCurrent.ownerToken)

        now = now.plusSeconds(6)
        manager.enforceSafetyTimeouts()

        val ownerLostCurrent = assertNotNull(manager.currentSnapshot("workspace-running-owner-loss-current"))
        assertEquals(started.id, ownerLostCurrent.id)
        assertEquals(SqlConsoleExecutionStatus.RUNNING, ownerLostCurrent.status)
        assertFinalControlPathCleared(ownerLostCurrent)

        val heartbeatError = assertFailsWith<UiStateConflictException> {
            manager.heartbeat(started.id, OWNER_SESSION_ID, ownerToken)
        }
        assertTrue(heartbeatError.message!!.contains("потеряла владельца"))

        releaseExecution.countDown()
        val finished = waitForCompletion(manager, started.id)
        assertEquals(SqlConsoleExecutionStatus.SUCCESS, finished.status)
        assertFinalControlPathCleared(finished)
    }

    private fun startPendingManualTransaction(
        manager: SqlConsoleQueryManager,
        workspaceId: String,
    ): SqlConsoleExecutionSnapshot {
        val started = manager.startQuery(
            sql = "update demo set flag = true",
            credentialsPath = null,
            selectedSourceNames = listOf("db1"),
            workspaceId = workspaceId,
            ownerSessionId = OWNER_SESSION_ID,
            transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
        )
        assertNotNull(started.ownerToken)
        val pending = waitForCompletion(manager, started.id)
        assertEquals(SqlConsoleExecutionTransactionState.PENDING_COMMIT, pending.transactionState)
        return pending
    }

    private fun assertFinalControlPathCleared(snapshot: SqlConsoleExecutionSnapshot) {
        assertNull(snapshot.ownerToken)
        assertNull(snapshot.ownerLeaseExpiresAt)
        assertNull(snapshot.pendingCommitExpiresAt)
    }
}
