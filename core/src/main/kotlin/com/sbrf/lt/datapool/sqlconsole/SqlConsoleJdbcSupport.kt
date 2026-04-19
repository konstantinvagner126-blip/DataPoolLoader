package com.sbrf.lt.datapool.sqlconsole

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class JdbcShardSqlExecutor : ShardSqlExecutor, ShardSqlScriptExecutor {
    override fun execute(
        shard: ResolvedSqlConsoleShardConfig,
        statement: SqlConsoleStatement,
        fetchSize: Int,
        maxRows: Int,
        queryTimeoutSec: Int?,
        executionControl: SqlConsoleExecutionControl,
    ): RawShardExecutionResult {
        DriverManager.getConnection(shard.jdbcUrl, shard.username, shard.password).use { connection ->
            return executeSql(connection, shard, statement, fetchSize, maxRows, queryTimeoutSec, executionControl)
        }
    }

    override fun executeScript(
        shard: ResolvedSqlConsoleShardConfig,
        statements: List<SqlConsoleStatement>,
        fetchSize: Int,
        maxRows: Int,
        queryTimeoutSec: Int?,
        executionPolicy: SqlConsoleExecutionPolicy,
        transactionMode: SqlConsoleTransactionMode,
        executionControl: SqlConsoleExecutionControl,
    ): List<RawShardExecutionResult> {
        require(transactionMode == SqlConsoleTransactionMode.TRANSACTION_PER_SHARD) {
            "Script executor поддерживает только TRANSACTION_PER_SHARD."
        }
        require(executionPolicy == SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR) {
            "TRANSACTION_PER_SHARD поддерживает только STOP_ON_FIRST_ERROR."
        }

        DriverManager.getConnection(shard.jdbcUrl, shard.username, shard.password).use { connection ->
            connection.autoCommit = false
            val results = mutableListOf<RawShardExecutionResult>()
            try {
                statements.forEachIndexed { index, statement ->
                    if (executionControl.isCancelled()) {
                        throw SqlConsoleExecutionCancelledException("Запрос отменен пользователем.")
                    }
                    val result = runCatching {
                        executeSql(connection, shard, statement, fetchSize, maxRows, queryTimeoutSec, executionControl)
                    }.getOrElse { ex ->
                        if (ex is SqlConsoleExecutionCancelledException) {
                            throw ex
                        }
                        rollbackQuietly(connection)
                        results += RawShardExecutionResult(
                            shardName = shard.name,
                            status = "FAILED",
                            errorMessage = ex.message ?: "Неизвестная ошибка",
                        )
                        repeat(statements.lastIndex - index) {
                            results += RawShardExecutionResult(
                                shardName = shard.name,
                                status = "SKIPPED",
                                message = "Statement пропущен из-за rollback после ошибки в транзакции.",
                            )
                        }
                        return results
                    }
                    results += result
                }
                connection.commit()
                return results
            } catch (ex: SqlConsoleExecutionCancelledException) {
                rollbackQuietly(connection)
                throw ex
            } catch (ex: Exception) {
                rollbackQuietly(connection)
                throw ex
            }
        }
    }

    private fun executeSql(
        connection: Connection,
        shard: ResolvedSqlConsoleShardConfig,
        statement: SqlConsoleStatement,
        fetchSize: Int,
        maxRows: Int,
        queryTimeoutSec: Int?,
        executionControl: SqlConsoleExecutionControl,
    ): RawShardExecutionResult {
        connection.createStatement().use { jdbcStatement ->
            executionControl.register(jdbcStatement)
            try {
                jdbcStatement.fetchSize = fetchSize
                jdbcStatement.maxRows = maxRows + 1
                jdbcStatement.queryTimeout = queryTimeoutSec ?: 0
                val hasResultSet = jdbcStatement.execute(statement.sql)
                if (hasResultSet) {
                    jdbcStatement.resultSet.use { resultSet ->
                        val metaData = resultSet.metaData
                        val columns = (1..metaData.columnCount).map { metaData.getColumnLabel(it) }
                        val rows = mutableListOf<Map<String, String?>>()
                        var truncated = false
                        while (resultSet.next()) {
                            if (rows.size == maxRows) {
                                truncated = true
                                break
                            }
                            rows += buildMap {
                                columns.forEachIndexed { index, column ->
                                    put(column, resultSet.getObject(index + 1)?.toString())
                                }
                            }
                        }
                        return RawShardExecutionResult(
                            shardName = shard.name,
                            status = "SUCCESS",
                            columns = columns,
                            rows = rows,
                            truncated = truncated,
                            message = if (truncated) {
                                "Результат усечен до $maxRows строк."
                            } else {
                                "Данные получены успешно."
                            },
                        )
                    }
                }
                return RawShardExecutionResult(
                    shardName = shard.name,
                    status = "SUCCESS",
                    affectedRows = jdbcStatement.updateCount.takeIf { it >= 0 },
                    message = "${statement.leadingKeyword} выполнен успешно.",
                )
            } catch (ex: SQLException) {
                if (executionControl.isCancelled()) {
                    throw SqlConsoleExecutionCancelledException("Запрос отменен пользователем.", ex)
                }
                throw ex
            } finally {
                executionControl.unregister(jdbcStatement)
            }
        }
    }

    private fun rollbackQuietly(connection: Connection) {
        runCatching { connection.rollback() }
    }
}

class JdbcShardConnectionChecker : ShardConnectionChecker {
    override fun check(
        shard: ResolvedSqlConsoleShardConfig,
        queryTimeoutSec: Int?,
    ): RawShardConnectionCheckResult {
        DriverManager.getConnection(shard.jdbcUrl, shard.username, shard.password).use { connection ->
            val isValid = runCatching {
                connection.isValid((queryTimeoutSec ?: 5).coerceIn(1, 30))
            }.getOrDefault(true)
            require(isValid) { "Подключение установлено, но валидация соединения не пройдена." }
        }
        return RawShardConnectionCheckResult(
            shardName = shard.name,
            status = "SUCCESS",
            message = "Подключение установлено.",
        )
    }
}

class SqlConsoleExecutionControl {
    private val cancelled = AtomicBoolean(false)
    private val statements = CopyOnWriteArrayList<Statement>()

    fun cancel() {
        if (cancelled.compareAndSet(false, true)) {
            statements.forEach {
                runCatching { it.cancel() }
            }
        }
    }

    fun register(statement: Statement) {
        statements += statement
        if (cancelled.get()) {
            runCatching { statement.cancel() }
        }
    }

    fun unregister(statement: Statement) {
        statements -= statement
    }

    fun isCancelled(): Boolean = cancelled.get()
}

class SqlConsoleExecutionCancelledException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
