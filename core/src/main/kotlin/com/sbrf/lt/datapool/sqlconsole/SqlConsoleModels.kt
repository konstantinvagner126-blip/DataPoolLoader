package com.sbrf.lt.datapool.sqlconsole

import com.fasterxml.jackson.annotation.JsonAlias
import java.time.Instant

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

enum class SqlConsoleExecutionPolicy {
    STOP_ON_FIRST_ERROR,
    CONTINUE_ON_ERROR,
}

enum class SqlConsoleTransactionMode {
    AUTO_COMMIT,
    TRANSACTION_PER_SHARD,
}

enum class SqlConsoleTransactionState {
    NONE,
    PENDING_COMMIT,
    COMMITTED,
    ROLLED_BACK,
}

enum class SqlConsoleDatabaseObjectType {
    TABLE,
    VIEW,
    MATERIALIZED_VIEW,
    INDEX,
    SEQUENCE,
    TRIGGER,
    SCHEMA,
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
    val connectionState: SqlConsoleConnectionState? = null,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val durationMillis: Long? = null,
)

data class RawShardConnectionCheckResult(
    val shardName: String,
    val status: String,
    val message: String? = null,
    val errorMessage: String? = null,
)

enum class SqlConsoleConnectionState {
    AVAILABLE,
    UNAVAILABLE,
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
    val statementResults: List<SqlConsoleStatementResult> = emptyList(),
)

data class SqlConsoleStatementResult(
    val sql: String,
    val statementType: SqlConsoleStatementType,
    val statementKeyword: String,
    val shardResults: List<RawShardExecutionResult>,
)

data class SqlConsoleConnectionCheckResult(
    val sourceResults: List<RawShardConnectionCheckResult>,
)

data class SqlConsoleDatabaseObjectSearchResult(
    val query: String,
    val sourceResults: List<SqlConsoleDatabaseObjectSourceResult>,
    val maxObjectsPerSource: Int,
)

data class SqlConsoleDatabaseObjectSourceResult(
    val sourceName: String,
    val status: String,
    val objects: List<SqlConsoleDatabaseObject> = emptyList(),
    val truncated: Boolean = false,
    val errorMessage: String? = null,
)

data class SqlConsoleDatabaseObject(
    val schemaName: String,
    val objectName: String,
    val objectType: SqlConsoleDatabaseObjectType,
    val tableName: String? = null,
)

data class SqlConsoleDatabaseObjectColumn(
    val name: String,
    val type: String,
    val nullable: Boolean,
)

data class SqlConsoleDatabaseObjectInspector(
    val schemaName: String,
    val objectName: String,
    val objectType: SqlConsoleDatabaseObjectType,
    val tableName: String? = null,
    val definition: String? = null,
    val columns: List<SqlConsoleDatabaseObjectColumn> = emptyList(),
    val indexes: List<SqlConsoleDatabaseObjectIndex> = emptyList(),
    val constraints: List<SqlConsoleDatabaseObjectConstraint> = emptyList(),
    val relatedTriggers: List<SqlConsoleDatabaseObjectTrigger> = emptyList(),
    val trigger: SqlConsoleDatabaseObjectTrigger? = null,
    val sequence: SqlConsoleDatabaseObjectSequence? = null,
    val schema: SqlConsoleDatabaseObjectSchema? = null,
)

data class SqlConsoleDatabaseObjectIndex(
    val name: String,
    val tableName: String? = null,
    val columns: List<String> = emptyList(),
    val unique: Boolean? = null,
    val primary: Boolean? = null,
    val definition: String? = null,
)

data class SqlConsoleDatabaseObjectConstraint(
    val name: String,
    val type: String,
    val columns: List<String> = emptyList(),
    val definition: String? = null,
)

data class SqlConsoleDatabaseObjectTrigger(
    val name: String,
    val targetObjectName: String? = null,
    val timing: String? = null,
    val events: List<String> = emptyList(),
    val enabled: Boolean? = null,
    val functionName: String? = null,
    val definition: String? = null,
)

data class SqlConsoleDatabaseObjectSequence(
    val incrementBy: String? = null,
    val minimumValue: String? = null,
    val maximumValue: String? = null,
    val startValue: String? = null,
    val cacheSize: String? = null,
    val cycle: Boolean? = null,
    val ownedBy: String? = null,
)

data class SqlConsoleDatabaseObjectSchema(
    val owner: String? = null,
    val comment: String? = null,
    val privileges: List<String> = emptyList(),
    val objectCounts: List<SqlConsoleDatabaseObjectCount> = emptyList(),
)

data class SqlConsoleDatabaseObjectCount(
    val label: String,
    val count: Int,
)

data class SqlConsoleExecutionRun(
    val result: SqlConsoleQueryResult,
    val pendingTransaction: SqlConsolePendingTransaction? = null,
)

interface SqlConsolePendingTransaction {
    val shardNames: List<String>

    fun commit()

    fun rollback()
}
