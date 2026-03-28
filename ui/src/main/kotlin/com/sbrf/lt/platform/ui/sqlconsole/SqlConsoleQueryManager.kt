package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionCancelledException
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleExecutionControl
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleQueryResult
import com.sbrf.lt.datapool.sqlconsole.SqlConsoleService
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class SqlConsoleExecutionStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

data class SqlConsoleExecutionSnapshot(
    val id: String,
    val status: SqlConsoleExecutionStatus,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val cancelRequested: Boolean = false,
    val result: SqlConsoleQueryResult? = null,
    val errorMessage: String? = null,
)

class SqlConsoleQueryManager(
    private val sqlConsoleService: SqlConsoleService,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
) {
    private val lock = Any()
    private var activeExecution: ActiveExecution? = null

    fun startQuery(
        sql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String> = emptyList(),
        cleanupDir: Path? = null,
    ): SqlConsoleExecutionSnapshot {
        val execution = synchronized(lock) {
            require(activeExecution?.snapshot?.status != SqlConsoleExecutionStatus.RUNNING) {
                "В SQL-консоли уже выполняется запрос. Дождись завершения или отмени его."
            }
            ActiveExecution(
                snapshot = SqlConsoleExecutionSnapshot(
                    id = UUID.randomUUID().toString(),
                    status = SqlConsoleExecutionStatus.RUNNING,
                    startedAt = Instant.now(),
                ),
                control = SqlConsoleExecutionControl(),
            ).also { activeExecution = it }
        }

        executor.submit {
            val finalSnapshot = try {
                execution.snapshot.copy(
                    status = SqlConsoleExecutionStatus.SUCCESS,
                    finishedAt = Instant.now(),
                    result = sqlConsoleService.executeQuery(
                        rawSql = sql,
                        credentialsPath = credentialsPath,
                        selectedSourceNames = selectedSourceNames,
                        executionControl = execution.control,
                    ),
                )
            } catch (ex: SqlConsoleExecutionCancelledException) {
                execution.snapshot.copy(
                    status = SqlConsoleExecutionStatus.CANCELLED,
                    finishedAt = Instant.now(),
                    cancelRequested = true,
                    errorMessage = ex.message ?: "Запрос отменен пользователем.",
                )
            } catch (ex: Exception) {
                val cancelled = execution.control.isCancelled()
                execution.snapshot.copy(
                    status = if (cancelled) SqlConsoleExecutionStatus.CANCELLED else SqlConsoleExecutionStatus.FAILED,
                    finishedAt = Instant.now(),
                    cancelRequested = cancelled || execution.snapshot.cancelRequested,
                    errorMessage = ex.message ?: "Не удалось выполнить запрос.",
                )
            }
            cleanupDir?.toFile()?.deleteRecursively()
            synchronized(lock) {
                if (activeExecution?.snapshot?.id == execution.snapshot.id) {
                    activeExecution = execution.copy(snapshot = finalSnapshot)
                }
            }
        }

        return execution.snapshot
    }

    fun currentSnapshot(): SqlConsoleExecutionSnapshot? = synchronized(lock) {
        activeExecution?.snapshot
    }

    fun snapshot(executionId: String): SqlConsoleExecutionSnapshot {
        return synchronized(lock) {
            val execution = activeExecution
            require(execution != null && execution.snapshot.id == executionId) {
                "Запуск SQL-консоли $executionId не найден."
            }
            execution.snapshot
        }
    }

    fun cancel(executionId: String): SqlConsoleExecutionSnapshot {
        val execution = synchronized(lock) {
            val current = activeExecution
            require(current != null && current.snapshot.id == executionId) {
                "Запуск SQL-консоли $executionId не найден."
            }
            require(current.snapshot.status == SqlConsoleExecutionStatus.RUNNING) {
                "Запрос SQL-консоли уже завершен."
            }
            current.control.cancel()
            val updated = current.copy(snapshot = current.snapshot.copy(cancelRequested = true))
            activeExecution = updated
            updated
        }
        return execution.snapshot
    }

    private data class ActiveExecution(
        val snapshot: SqlConsoleExecutionSnapshot,
        val control: SqlConsoleExecutionControl,
    )
}
