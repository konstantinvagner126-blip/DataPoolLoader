package com.sbrf.lt.datapool.sqlconsole

import com.fasterxml.jackson.annotation.JsonAlias
import com.sbrf.lt.datapool.config.ValueResolver
import org.slf4j.LoggerFactory
import java.nio.file.Path

data class SqlConsoleConfig(
    val fetchSize: Int = 1000,
    val maxRowsPerShard: Int = 200,
    val queryTimeoutSec: Int? = null,
    @param:JsonAlias("shards")
    val sources: List<SqlConsoleSourceConfig> = emptyList(),
)

data class SqlConsoleSourceConfig(
    val name: String = "",
    val jdbcUrl: String = "",
    val username: String = "",
    val password: String = "",
)

data class ResolvedSqlConsoleShardConfig(
    val name: String,
    val jdbcUrl: String,
    val username: String,
    val password: String,
)

enum class SqlConsoleStatementType {
    RESULT_SET,
    COMMAND,
}

data class SqlConsoleStatement(
    val sql: String,
    val leadingKeyword: String,
)

data class RawShardExecutionResult(
    val shardName: String,
    val status: String,
    val columns: List<String> = emptyList(),
    val rows: List<Map<String, String?>> = emptyList(),
    val truncated: Boolean = false,
    val affectedRows: Int? = null,
    val message: String? = null,
    val errorMessage: String? = null,
)

data class RawShardConnectionCheckResult(
    val shardName: String,
    val status: String,
    val message: String? = null,
    val errorMessage: String? = null,
)

fun interface ShardSqlExecutor {
    fun execute(
        shard: ResolvedSqlConsoleShardConfig,
        statement: SqlConsoleStatement,
        fetchSize: Int,
        maxRows: Int,
        queryTimeoutSec: Int?,
        executionControl: SqlConsoleExecutionControl,
    ): RawShardExecutionResult
}

fun interface ShardConnectionChecker {
    fun check(
        shard: ResolvedSqlConsoleShardConfig,
        queryTimeoutSec: Int?,
    ): RawShardConnectionCheckResult
}

data class SqlConsoleInfo(
    val configured: Boolean,
    val sourceNames: List<String>,
    val maxRowsPerShard: Int,
    val queryTimeoutSec: Int?,
)

data class SqlConsoleQueryResult(
    val sql: String,
    val statementType: SqlConsoleStatementType,
    val statementKeyword: String,
    val shardResults: List<RawShardExecutionResult>,
    val maxRowsPerShard: Int,
)

data class SqlConsoleConnectionCheckResult(
    val sourceResults: List<RawShardConnectionCheckResult>,
)

class SqlConsoleService(
    config: SqlConsoleConfig,
    private val executor: ShardSqlExecutor = JdbcShardSqlExecutor(),
    private val connectionChecker: ShardConnectionChecker = JdbcShardConnectionChecker(),
) : SqlConsoleOperations {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val support = SqlConsoleConfigSupport()
    @Volatile
    private var currentConfig: SqlConsoleConfig = config

    override fun info(): SqlConsoleInfo {
        val config = currentConfig
        return SqlConsoleInfo(
            configured = config.sources.isNotEmpty(),
            sourceNames = config.sources.map { it.name },
            maxRowsPerShard = config.maxRowsPerShard,
            queryTimeoutSec = config.queryTimeoutSec,
        )
    }

    override fun updateMaxRowsPerShard(maxRowsPerShard: Int): SqlConsoleInfo {
        return updateSettings(maxRowsPerShard, currentConfig.queryTimeoutSec)
    }

    override fun updateSettings(
        maxRowsPerShard: Int,
        queryTimeoutSec: Int?,
    ): SqlConsoleInfo {
        require(maxRowsPerShard > 0) { "Лимит строк на source должен быть больше 0." }
        require(queryTimeoutSec == null || queryTimeoutSec > 0) {
            "Таймаут запроса на source должен быть больше 0, если задан."
        }
        currentConfig = currentConfig.copy(
            maxRowsPerShard = maxRowsPerShard,
            queryTimeoutSec = queryTimeoutSec,
        )
        return info()
    }

    override fun executeQuery(
        rawSql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
        executionControl: SqlConsoleExecutionControl,
    ): SqlConsoleQueryResult {
        val config = currentConfig
        val statement = support.parseAndValidateStatement(rawSql)
        val resolvedShards = support.resolveSources(config, credentialsPath, selectedSourceNames)

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
            statementType = support.determineResultType(shardResults),
            statementKeyword = statement.leadingKeyword,
            shardResults = shardResults,
            maxRowsPerShard = config.maxRowsPerShard,
        )
    }

    override fun checkConnections(
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
    ): SqlConsoleConnectionCheckResult {
        val config = currentConfig
        val selectedSources = support.selectSources(config, selectedSourceNames)
        val resolver = ValueResolver.fromFile(credentialsPath)
        val results = selectedSources.map { source ->
            runCatching {
                val shard = support.resolveSource(source, resolver)
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
