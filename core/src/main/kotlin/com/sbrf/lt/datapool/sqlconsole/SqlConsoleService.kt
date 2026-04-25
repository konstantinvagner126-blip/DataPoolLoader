package com.sbrf.lt.datapool.sqlconsole

import org.slf4j.LoggerFactory
import java.nio.file.Path

class SqlConsoleService(
    config: SqlConsoleConfig,
    private val executor: ShardSqlExecutor = JdbcShardSqlExecutor(),
    private val connectionChecker: ShardConnectionChecker = JdbcShardConnectionChecker(),
    private val objectSearcher: ShardSqlObjectSearcher = JdbcShardSqlObjectSearcher(),
    private val objectInspector: ShardSqlObjectInspector = JdbcShardSqlObjectSearcher(),
    private val objectColumnLoader: ShardSqlObjectColumnLoader = JdbcShardSqlObjectSearcher(),
) : SqlConsoleOperations, SqlConsoleTransactionalOperations {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val configSupport = SqlConsoleConfigSupport()
    private val stateSupport = SqlConsoleStateSupport()
    private val executionSupport = SqlConsoleExecutionSupport(
        configSupport = configSupport,
        executor = executor,
        connectionChecker = connectionChecker,
        logger = logger,
    )
    private val metadataSupport = SqlConsoleMetadataSupport(
        configSupport = configSupport,
        objectSearcher = objectSearcher,
        objectInspector = objectInspector,
        objectColumnLoader = objectColumnLoader,
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

    override fun updateConfig(config: SqlConsoleConfig): SqlConsoleInfo {
        currentConfig = config
        return info()
    }

    override fun executeQuery(
        rawSql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
        executionPolicy: SqlConsoleExecutionPolicy,
        transactionMode: SqlConsoleTransactionMode,
        executionControl: SqlConsoleExecutionControl,
    ): SqlConsoleQueryResult =
        executionSupport.executeQuery(
            config = currentConfig,
            rawSql = rawSql,
            credentialsPath = credentialsPath,
            selectedSourceNames = selectedSourceNames,
            executionPolicy = executionPolicy,
            transactionMode = transactionMode,
            executionControl = executionControl,
        )

    override fun executeQueryRun(
        rawSql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
        autoCommitEnabled: Boolean,
        executionControl: SqlConsoleExecutionControl,
    ): SqlConsoleExecutionRun =
        executionSupport.executeQueryRun(
            config = currentConfig,
            rawSql = rawSql,
            credentialsPath = credentialsPath,
            selectedSourceNames = selectedSourceNames,
            autoCommitEnabled = autoCommitEnabled,
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

    override fun searchObjects(
        rawQuery: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
        maxObjectsPerSource: Int,
    ): SqlConsoleDatabaseObjectSearchResult =
        metadataSupport.searchObjects(
            config = currentConfig,
            rawQuery = rawQuery,
            credentialsPath = credentialsPath,
            selectedSourceNames = selectedSourceNames,
            maxObjectsPerSource = maxObjectsPerSource,
        )

    override fun inspectObject(
        sourceName: String,
        schemaName: String,
        objectName: String,
        objectType: SqlConsoleDatabaseObjectType,
        credentialsPath: Path?,
    ): SqlConsoleDatabaseObjectInspector =
        metadataSupport.inspectObject(
            config = currentConfig,
            sourceName = sourceName,
            schemaName = schemaName,
            objectName = objectName,
            objectType = objectType,
            credentialsPath = credentialsPath,
        )

    override fun loadObjectColumns(
        schemaName: String,
        objectName: String,
        objectType: SqlConsoleDatabaseObjectType,
        credentialsPath: Path?,
        selectedSourceNames: List<String>,
    ): SqlConsoleDatabaseObjectColumnLookupResult =
        metadataSupport.loadObjectColumns(
            config = currentConfig,
            schemaName = schemaName,
            objectName = objectName,
            objectType = objectType,
            credentialsPath = credentialsPath,
            selectedSourceNames = selectedSourceNames,
        )
}
