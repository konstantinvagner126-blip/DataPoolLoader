package com.sbrf.lt.datapool.sqlconsole

import org.slf4j.LoggerFactory
import java.nio.file.Path

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
