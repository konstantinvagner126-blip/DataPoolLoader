package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionPolicy
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleOperations
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionalOperations
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class SqlConsoleExecutionStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

class SqlConsoleQueryManager(
    private val sqlConsoleService: SqlConsoleOperations,
    private val transactionalOperations: SqlConsoleTransactionalOperations = sqlConsoleService as? SqlConsoleTransactionalOperations
        ?: error("SQL-консоль не поддерживает транзакционные execution session."),
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) : SqlConsoleAsyncQueryOperations {
    private val stateSupport = SqlConsoleQueryStateSupport()
    private val executionSupport = SqlConsoleQueryExecutionSupport(sqlConsoleService, transactionalOperations)
    private val transactionSupport = SqlConsoleQueryTransactionSupport(stateSupport)

    override fun startQuery(
        sql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
        executionPolicy: SqlConsoleExecutionPolicy,
        transactionMode: SqlConsoleTransactionMode,
        cleanupDir: Path?,
    ): SqlConsoleExecutionSnapshot {
        val execution = stateSupport.prepareStart(
            autoCommitEnabled = transactionMode == SqlConsoleTransactionMode.AUTO_COMMIT,
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
            stateSupport.storeCompletedExecution(execution.snapshot.id, finalExecution)
        }

        return execution.snapshot
    }

    override fun currentSnapshot(): SqlConsoleExecutionSnapshot? = stateSupport.currentSnapshot()

    override fun snapshot(executionId: String): SqlConsoleExecutionSnapshot = stateSupport.snapshot(executionId)

    override fun cancel(executionId: String): SqlConsoleExecutionSnapshot = stateSupport.cancel(executionId)

    override fun commit(executionId: String): SqlConsoleExecutionSnapshot = transactionSupport.commit(executionId)

    override fun rollback(executionId: String): SqlConsoleExecutionSnapshot = transactionSupport.rollback(executionId)
}
