package com.sbrf.lt.datapool.sqlconsole

import com.fasterxml.jackson.annotation.JsonAlias
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
    private val configSupport = SqlConsoleConfigSupport()
    private val stateSupport = SqlConsoleStateSupport()
    private val executionSupport = SqlConsoleExecutionSupport(
        configSupport = configSupport,
        executor = executor,
        connectionChecker = connectionChecker,
        logger = logger,
    )
    @Volatile
    private var currentConfig: SqlConsoleConfig = config

    override fun info(): SqlConsoleInfo = stateSupport.info(currentConfig)

    override fun updateMaxRowsPerShard(maxRowsPerShard: Int): SqlConsoleInfo {
        return updateSettings(maxRowsPerShard, currentConfig.queryTimeoutSec)
    }

    override fun updateSettings(
        maxRowsPerShard: Int,
        queryTimeoutSec: Int?,
    ): SqlConsoleInfo {
        currentConfig = stateSupport.updateSettings(
            currentConfig = currentConfig,
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
    ): SqlConsoleQueryResult =
        executionSupport.executeQuery(
            config = currentConfig,
            rawSql = rawSql,
            credentialsPath = credentialsPath,
            selectedSourceNames = selectedSourceNames,
            executionControl = executionControl,
        )

    override fun checkConnections(
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
    ): SqlConsoleConnectionCheckResult =
        executionSupport.checkConnections(
            config = currentConfig,
            credentialsPath = credentialsPath,
            selectedSourceNames = selectedSourceNames,
        )
}
