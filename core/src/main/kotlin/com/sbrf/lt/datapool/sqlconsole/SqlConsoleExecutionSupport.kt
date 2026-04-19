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
        executionControl: SqlConsoleExecutionControl,
    ): SqlConsoleQueryResult {
        val statement = configSupport.parseAndValidateStatement(rawSql)
        val resolvedShards = configSupport.resolveSources(config, credentialsPath, selectedSourceNames)

        val shardResults = resolvedShards.map { shard ->
            if (executionControl.isCancelled()) {
                throw SqlConsoleExecutionCancelledException("Запрос отменен пользователем.")
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
            }
        }

        return SqlConsoleQueryResult(
            sql = statement.sql,
            statementType = configSupport.determineResultType(shardResults),
            statementKeyword = statement.leadingKeyword,
            shardResults = shardResults,
            maxRowsPerShard = config.maxRowsPerShard,
        )
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
}
