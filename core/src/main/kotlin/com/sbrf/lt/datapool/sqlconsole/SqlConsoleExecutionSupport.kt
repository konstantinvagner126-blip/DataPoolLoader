package com.sbrf.lt.datapool.sqlconsole

import com.sbrf.lt.datapool.config.ValueResolver
import org.slf4j.Logger
import java.nio.file.Path
import java.time.Instant

internal class SqlConsoleExecutionSupport(
    private val configSupport: SqlConsoleConfigSupport,
    private val executor: ShardSqlExecutor,
    private val connectionChecker: ShardConnectionChecker,
    private val logger: Logger,
) {
    fun executeQuery(
        config: SqlConsoleConfig,
        rawSql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
        executionPolicy: SqlConsoleExecutionPolicy,
        transactionMode: SqlConsoleTransactionMode,
        executionControl: SqlConsoleExecutionControl,
    ): SqlConsoleQueryResult {
        val statements = configSupport.parseAndValidateStatements(rawSql)
        val resolvedShards = configSupport.resolveSources(config, credentialsPath, selectedSourceNames)
        if (transactionMode == SqlConsoleTransactionMode.TRANSACTION_PER_SHARD) {
            return executeTransactionalQuery(
                config = config,
                rawSql = rawSql,
                statements = statements,
                resolvedShards = resolvedShards,
                executionPolicy = executionPolicy,
                executionControl = executionControl,
            ).result
        }
        return executeAutoCommitQuery(
            config = config,
            rawSql = rawSql,
            statements = statements,
            resolvedShards = resolvedShards,
            executionPolicy = executionPolicy,
            executionControl = executionControl,
        )
    }

    fun executeQueryRun(
        config: SqlConsoleConfig,
        rawSql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
        autoCommitEnabled: Boolean,
        executionControl: SqlConsoleExecutionControl,
    ): SqlConsoleExecutionRun {
        val statements = configSupport.parseAndValidateStatements(rawSql)
        val resolvedShards = configSupport.resolveSources(config, credentialsPath, selectedSourceNames)
        return if (autoCommitEnabled) {
            SqlConsoleExecutionRun(
                result = executeAutoCommitQuery(
                    config = config,
                    rawSql = rawSql,
                    statements = statements,
                    resolvedShards = resolvedShards,
                    executionPolicy = SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR,
                    executionControl = executionControl,
                ),
            )
        } else {
            executeTransactionalQuery(
                config = config,
                rawSql = rawSql,
                statements = statements,
                resolvedShards = resolvedShards,
                executionPolicy = SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR,
                executionControl = executionControl,
                keepTransactionOpenOnSuccess = true,
            )
        }
    }

    fun checkConnections(
        config: SqlConsoleConfig,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
    ): SqlConsoleConnectionCheckResult {
        val selectedSources = configSupport.selectSources(config, selectedSourceNames)
        val resolver = ValueResolver.fromFile(credentialsPath)
        val results = selectedSources.map { source ->
            runCatching {
                val shard = configSupport.resolveSource(source, resolver)
                connectionChecker.check(shard, config.queryTimeoutSec)
            }.onFailure { ex ->
                logger.warn("SQL-консоль: проверка подключения shard {} завершилась ошибкой: {}", source.name, ex.message)
            }.getOrElse { ex ->
                RawShardConnectionCheckResult(
                    shardName = source.name,
                    status = "FAILED",
                    errorMessage = ex.message ?: "Не удалось установить подключение.",
                )
            }
        }
        return SqlConsoleConnectionCheckResult(sourceResults = results)
    }

    private fun buildQueryResult(
        rawSql: String,
        maxRowsPerShard: Int,
        statementResults: List<SqlConsoleStatementResult>,
    ): SqlConsoleQueryResult {
        val primaryResult = statementResults.singleOrNull()
        val fallbackResult = statementResults.lastOrNull()
            ?: error("SQL-консоль не смогла построить ни одного statement result.")

        return SqlConsoleQueryResult(
            sql = if (statementResults.size == 1) fallbackResult.sql else rawSql.trim(),
            statementType = primaryResult?.statementType ?: SqlConsoleStatementType.COMMAND,
            statementKeyword = primaryResult?.statementKeyword ?: "SCRIPT",
            shardResults = primaryResult?.shardResults ?: fallbackResult.shardResults,
            maxRowsPerShard = maxRowsPerShard,
            statementResults = statementResults,
        )
    }

    private fun RawShardExecutionResult.withTiming(
        startedAt: Instant,
        finishedAt: Instant,
    ): RawShardExecutionResult =
        copy(
            startedAt = this.startedAt ?: startedAt,
            finishedAt = this.finishedAt ?: finishedAt,
            durationMillis = this.durationMillis ?: (finishedAt.toEpochMilli() - startedAt.toEpochMilli()).coerceAtLeast(0L),
        )

    private fun executeAutoCommitQuery(
        config: SqlConsoleConfig,
        rawSql: String,
        statements: List<SqlConsoleStatement>,
        resolvedShards: List<ResolvedSqlConsoleShardConfig>,
        executionPolicy: SqlConsoleExecutionPolicy,
        executionControl: SqlConsoleExecutionControl,
    ): SqlConsoleQueryResult {
        val blockedShards = mutableSetOf<String>()
        val statementResults = statements.map { statement ->
            val shardResults = resolvedShards.map { shard ->
                if (executionControl.isCancelled()) {
                    throw SqlConsoleExecutionCancelledException("Запрос отменен пользователем.")
                }
                if (executionPolicy == SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR && shard.name in blockedShards) {
                    return@map RawShardExecutionResult(
                        shardName = shard.name,
                        status = "SKIPPED",
                        message = "Statement пропущен из-за ошибки в предыдущем statement.",
                    )
                }
                val startedAt = Instant.now()
                runCatching {
                    executor.execute(
                        shard = shard,
                        statement = statement,
                        fetchSize = config.fetchSize,
                        maxRows = config.maxRowsPerShard,
                        queryTimeoutSec = config.queryTimeoutSec,
                        executionControl = executionControl,
                    )
                }.onFailure { ex ->
                    if (ex is SqlConsoleExecutionCancelledException) {
                        throw ex
                    }
                    logger.warn("SQL-консоль: shard {} завершился ошибкой: {}", shard.name, ex.message)
                }.getOrElse { ex ->
                    RawShardExecutionResult(
                        shardName = shard.name,
                        status = "FAILED",
                        errorMessage = ex.message ?: "Неизвестная ошибка",
                        connectionState = classifyExecutionConnectionState(ex),
                    )
                }.let { result ->
                    val finishedAt = Instant.now()
                    result.withTiming(startedAt, finishedAt)
                }.also { result ->
                    if (result.status.equals("FAILED", ignoreCase = true)) {
                        blockedShards += shard.name
                    }
                }
            }
            SqlConsoleStatementResult(
                sql = statement.sql,
                statementType = configSupport.determineResultType(shardResults),
                statementKeyword = statement.leadingKeyword,
                shardResults = shardResults,
            )
        }
        return buildQueryResult(rawSql, config.maxRowsPerShard, statementResults)
    }

    private fun executeTransactionalQuery(
        config: SqlConsoleConfig,
        rawSql: String,
        statements: List<SqlConsoleStatement>,
        resolvedShards: List<ResolvedSqlConsoleShardConfig>,
        executionPolicy: SqlConsoleExecutionPolicy,
        executionControl: SqlConsoleExecutionControl,
        keepTransactionOpenOnSuccess: Boolean = false,
    ): SqlConsoleExecutionRun {
        require(executionPolicy == SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR) {
            "Транзакционный режим поддерживает только политику «Остановить shard после ошибки»."
        }
        if (!keepTransactionOpenOnSuccess) {
            val scriptExecutor = executor as? ShardSqlScriptExecutor
                ?: error("Текущий executor SQL-консоли не поддерживает транзакционный режим.")
            val shardResultsByStatement = resolvedShards.associateWith { shard ->
                scriptExecutor.executeScript(
                    shard = shard,
                    statements = statements,
                    fetchSize = config.fetchSize,
                    maxRows = config.maxRowsPerShard,
                    queryTimeoutSec = config.queryTimeoutSec,
                    executionPolicy = executionPolicy,
                    transactionMode = SqlConsoleTransactionMode.TRANSACTION_PER_SHARD,
                    executionControl = executionControl,
                )
            }
            return SqlConsoleExecutionRun(
                result = buildQueryResult(
                    rawSql = rawSql,
                    maxRowsPerShard = config.maxRowsPerShard,
                    statementResults = buildStatementResults(statements, resolvedShards, shardResultsByStatement),
                ),
            )
        }

        val transactionalExecutor = executor as? ShardSqlTransactionalExecutor
            ?: error("Текущий executor SQL-консоли не поддерживает ручное управление транзакцией.")
        val shardExecutions = linkedMapOf<ResolvedSqlConsoleShardConfig, TransactionalShardScriptExecution>()
        val openedTransactions = mutableListOf<PendingShardTransaction>()
        resolvedShards.forEach { shard ->
            if (executionControl.isCancelled()) {
                rollbackQuietly(openedTransactions)
                throw SqlConsoleExecutionCancelledException("Запрос отменен пользователем.")
            }
            val shardExecution = transactionalExecutor.executeScriptInTransaction(
                shard = shard,
                statements = statements,
                fetchSize = config.fetchSize,
                maxRows = config.maxRowsPerShard,
                queryTimeoutSec = config.queryTimeoutSec,
                executionPolicy = executionPolicy,
                executionControl = executionControl,
            )
            shardExecutions[shard] = shardExecution
            if (shardExecution.pendingTransaction != null) {
                openedTransactions += shardExecution.pendingTransaction
            }
            if (shardExecution.results.any { it.status.equals("FAILED", ignoreCase = true) }) {
                rollbackQuietly(openedTransactions)
                val adjustedShardExecutions = shardExecutions.mapValues { (_, execution) ->
                    execution.copy(results = execution.results.map(::markRolledBackAfterGlobalFailure))
                }
                return SqlConsoleExecutionRun(
                    result = buildQueryResult(
                        rawSql = rawSql,
                        maxRowsPerShard = config.maxRowsPerShard,
                        statementResults = buildStatementResults(statements, resolvedShards, adjustedShardExecutions.mapValues { it.value.results }),
                    ),
                )
            }
        }
        if (!configSupport.requiresManualCommit(statements)) {
            openedTransactions.forEach { transaction ->
                transaction.commit()
            }
            return SqlConsoleExecutionRun(
                result = buildQueryResult(
                    rawSql = rawSql,
                    maxRowsPerShard = config.maxRowsPerShard,
                    statementResults = buildStatementResults(statements, resolvedShards, shardExecutions.mapValues { it.value.results }),
                ),
            )
        }
        return SqlConsoleExecutionRun(
            result = buildQueryResult(
                rawSql = rawSql,
                maxRowsPerShard = config.maxRowsPerShard,
                statementResults = buildStatementResults(statements, resolvedShards, shardExecutions.mapValues { it.value.results }),
            ),
            pendingTransaction = CompositeSqlConsolePendingTransaction(openedTransactions),
        )
    }

    private fun buildStatementResults(
        statements: List<SqlConsoleStatement>,
        resolvedShards: List<ResolvedSqlConsoleShardConfig>,
        shardResultsByStatement: Map<ResolvedSqlConsoleShardConfig, List<RawShardExecutionResult>>,
    ): List<SqlConsoleStatementResult> =
        statements.mapIndexed { index, statement ->
            SqlConsoleStatementResult(
                sql = statement.sql,
                statementType = configSupport.determineResultType(
                    resolvedShards.map { shard -> shardResultsByStatement.getValue(shard)[index] },
                ),
                statementKeyword = statement.leadingKeyword,
                shardResults = resolvedShards.map { shard -> shardResultsByStatement.getValue(shard)[index] },
            )
        }

    private fun rollbackQuietly(transactions: List<PendingShardTransaction>) {
        transactions.asReversed().forEach { transaction ->
            runCatching { transaction.rollback() }
        }
    }

    private fun markRolledBackAfterGlobalFailure(result: RawShardExecutionResult): RawShardExecutionResult =
        if (!result.status.equals("SUCCESS", ignoreCase = true)) {
            result
        } else {
            result.copy(message = appendRollbackNote(result.message))
        }

    private fun appendRollbackNote(message: String?): String =
        listOfNotNull(
            message?.takeIf { it.isNotBlank() },
            "Изменения откатены из-за ошибки на другом shard.",
        ).joinToString(" ")

    private class CompositeSqlConsolePendingTransaction(
        private val shardTransactions: List<PendingShardTransaction>,
    ) : SqlConsolePendingTransaction {
        override val shardNames: List<String> = shardTransactions.map { it.shardName }

        override fun commit() {
            var committedCount = 0
            try {
                shardTransactions.forEach { transaction ->
                    transaction.commit()
                    committedCount += 1
                }
            } catch (ex: Exception) {
                shardTransactions.drop(committedCount).forEach { transaction ->
                    runCatching { transaction.rollback() }
                }
                throw ex
            }
        }

        override fun rollback() {
            shardTransactions.asReversed().forEach { transaction ->
                transaction.rollback()
            }
        }
    }
}
