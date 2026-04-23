package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionalOperations
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

enum class SqlConsoleExecutionStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

internal class SqlConsoleQueryManager(
    private val sqlConsoleService: SqlConsoleOperations,
    private val transactionalOperations: SqlConsoleTransactionalOperations = sqlConsoleService as? SqlConsoleTransactionalOperations
        ?: error("SQL-консоль не поддерживает транзакционные execution session."),
    private val executionHistoryService: SqlConsoleExecutionHistoryService? = null,
    private val executor: ExecutorService = Executors.newCachedThreadPool(),
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val clock: () -> Instant = Instant::now,
    ownerLeaseDuration: Duration = Duration.ofSeconds(15),
    ownerReleaseRecoveryWindow: Duration = Duration.ofSeconds(5),
    pendingCommitTtl: Duration = Duration.ofMinutes(2),
) : SqlConsoleAsyncQueryOperations {
    private val stateSupport = SqlConsoleQueryStateSupport(
        executionHistoryService = executionHistoryService,
        ownerLeaseDuration = ownerLeaseDuration,
        ownerReleaseRecoveryWindow = ownerReleaseRecoveryWindow,
        pendingCommitTtl = pendingCommitTtl,
    )
    private val executionSupport = SqlConsoleQueryExecutionSupport(sqlConsoleService, transactionalOperations)
    private val transactionSupport = SqlConsoleQueryTransactionSupport(stateSupport)

    init {
        scheduler.scheduleWithFixedDelay(
            { runCatching { enforceSafetyTimeouts() } },
            1,
            1,
            TimeUnit.SECONDS,
        )
    }

    override fun startQuery(
        sql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
        workspaceId: String?,
        ownerSessionId: String,
        executionPolicy: SqlConsoleExecutionPolicy,
        transactionMode: SqlConsoleTransactionMode,
        cleanupDir: Path?,
    ): SqlConsoleExecutionSnapshot {
        enforceSafetyTimeouts()
        val execution = stateSupport.prepareStart(
            autoCommitEnabled = transactionMode == SqlConsoleTransactionMode.AUTO_COMMIT,
            sql = sql,
            selectedSourceNames = selectedSourceNames,
            workspaceId = workspaceId,
            ownerSessionId = ownerSessionId,
            now = clock(),
        )

        executor.submit {
            val finalExecution = executionSupport.execute(
                execution = execution,
                sql = sql,
                credentialsPath = credentialsPath,
                selectedSourceNames = selectedSourceNames,
                executionPolicy = SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR,
                transactionMode = transactionMode,
                cleanupDir = cleanupDir,
            )
            stateSupport.storeCompletedExecution(execution.snapshot.id, finalExecution, clock())
        }

        return execution.snapshot
    }

    override fun currentSnapshot(workspaceId: String?): SqlConsoleExecutionSnapshot? {
        enforceSafetyTimeouts()
        return stateSupport.currentSnapshot(workspaceId)
    }

    override fun snapshot(executionId: String): SqlConsoleExecutionSnapshot {
        enforceSafetyTimeouts()
        return stateSupport.snapshot(executionId)
    }

    override fun heartbeat(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsoleExecutionSnapshot {
        enforceSafetyTimeouts()
        return stateSupport.heartbeat(executionId, ownerSessionId, ownerToken, clock())
    }

    override fun releaseOwnership(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsoleExecutionSnapshot {
        enforceSafetyTimeouts()
        return stateSupport.releaseOwnership(executionId, ownerSessionId, ownerToken, clock())
    }

    override fun cancel(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsoleExecutionSnapshot {
        enforceSafetyTimeouts()
        return stateSupport.cancel(executionId, ownerSessionId, ownerToken)
    }

    override fun commit(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsoleExecutionSnapshot {
        enforceSafetyTimeouts()
        return transactionSupport.commit(executionId, ownerSessionId, ownerToken)
    }

    override fun rollback(
        executionId: String,
        ownerSessionId: String,
        ownerToken: String,
    ): SqlConsoleExecutionSnapshot {
        enforceSafetyTimeouts()
        return transactionSupport.rollback(executionId, ownerSessionId, ownerToken)
    }

    internal fun enforceSafetyTimeouts(): SqlConsoleExecutionSnapshot? =
        stateSupport.enforceSafetyTimeouts(clock())
}
