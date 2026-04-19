package com.sbrf.lt.datapool.sqlconsole

import com.sbrf.lt.datapool.config.ValueResolver
import org.slf4j.Logger
import java.nio.file.Path

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
            require(executionPolicy == SqlConsoleExecutionPolicy.STOP_ON_FIRST_ERROR) {
                "Транзакционный режим поддерживает только политику «Остановить shard после ошибки»."
            }
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
                    transactionMode = transactionMode,
                    executionControl = executionControl,
                )
            }
            val statementResults = statements.mapIndexed { index, statement ->
                SqlConsoleStatementResult(
                    sql = statement.sql,
                    statementType = configSupport.determineResultType(
                        resolvedShards.map { shard -> shardResultsByStatement.getValue(shard)[index] },
                    ),
                    statementKeyword = statement.leadingKeyword,
                    shardResults = resolvedShards.map { shard -> shardResultsByStatement.getValue(shard)[index] },
                )
            }
            return buildQueryResult(rawSql, config.maxRowsPerShard, statementResults)
        }
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
                    )
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
}
