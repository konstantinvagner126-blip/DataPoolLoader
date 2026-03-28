package com.sbrf.lt.datapool.sqlconsole

import com.fasterxml.jackson.annotation.JsonAlias
import com.sbrf.lt.datapool.config.ValueResolver
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

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
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    @Volatile
    private var currentConfig: SqlConsoleConfig = config

    fun info(): SqlConsoleInfo {
        val config = currentConfig
        return SqlConsoleInfo(
            configured = config.sources.isNotEmpty(),
            sourceNames = config.sources.map { it.name },
            maxRowsPerShard = config.maxRowsPerShard,
            queryTimeoutSec = config.queryTimeoutSec,
        )
    }

    fun updateMaxRowsPerShard(maxRowsPerShard: Int): SqlConsoleInfo {
        return updateSettings(maxRowsPerShard, currentConfig.queryTimeoutSec)
    }

    fun updateSettings(
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

    fun executeQuery(
        rawSql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String> = emptyList(),
        executionControl: SqlConsoleExecutionControl = SqlConsoleExecutionControl(),
    ): SqlConsoleQueryResult {
        val config = currentConfig
        val statement = parseAndValidateStatement(rawSql)
        val resolvedShards = resolveSources(config, credentialsPath, selectedSourceNames)

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
            statementType = determineResultType(shardResults),
            statementKeyword = statement.leadingKeyword,
            shardResults = shardResults,
            maxRowsPerShard = config.maxRowsPerShard,
        )
    }

    fun checkConnections(
        credentialsPath: Path?,
        selectedSourceNames: List<String> = emptyList(),
    ): SqlConsoleConnectionCheckResult {
        val config = currentConfig
        validateConfig(config)
        val selectedSources = selectSources(config, selectedSourceNames)
        val resolver = ValueResolver.fromFile(credentialsPath)
        val results = selectedSources.map { source ->
            runCatching {
                connectionChecker.check(resolveSource(source, resolver), config.queryTimeoutSec)
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

    private fun determineResultType(results: List<RawShardExecutionResult>): SqlConsoleStatementType {
        return if (results.any { it.columns.isNotEmpty() || it.rows.isNotEmpty() }) {
            SqlConsoleStatementType.RESULT_SET
        } else {
            SqlConsoleStatementType.COMMAND
        }
    }

    private fun resolveSources(
        config: SqlConsoleConfig,
        credentialsPath: Path?,
        selectedSourceNames: List<String> = emptyList(),
    ): List<ResolvedSqlConsoleShardConfig> {
        validateConfig(config)
        val selectedSources = selectSources(config, selectedSourceNames)
        val resolver = ValueResolver.fromFile(credentialsPath)
        return selectedSources.map { resolveSource(it, resolver) }
    }

    private fun validateConfig(config: SqlConsoleConfig) {
        require(config.fetchSize > 0) { "Параметр ui.sqlConsole.fetchSize должен быть больше 0." }
        require(config.maxRowsPerShard > 0) { "Параметр ui.sqlConsole.maxRowsPerShard должен быть больше 0." }
        require(config.queryTimeoutSec == null || config.queryTimeoutSec > 0) {
            "Параметр ui.sqlConsole.queryTimeoutSec должен быть больше 0, если задан."
        }
        require(config.sources.isNotEmpty()) {
            "В конфиге UI не настроены source-подключения. Заполни ui.sqlConsole.sources."
        }
    }

    private fun selectSources(
        config: SqlConsoleConfig,
        selectedSourceNames: List<String> = emptyList(),
    ): List<SqlConsoleSourceConfig> {
        val normalizedSelectedSourceNames = selectedSourceNames.map { it.trim() }.filter { it.isNotEmpty() }
        val selectedSources = if (normalizedSelectedSourceNames.isEmpty()) {
            config.sources
        } else {
            val configuredSourceNames = config.sources.map { it.name }.toSet()
            val unknown = normalizedSelectedSourceNames.filter { it !in configuredSourceNames }
            require(unknown.isEmpty()) {
                "В SQL-консоли выбраны неизвестные sources: ${unknown.joinToString(", ")}"
            }
            config.sources.filter { it.name in normalizedSelectedSourceNames }
        }
        require(selectedSources.isNotEmpty()) {
            "В SQL-консоли должен быть выбран хотя бы один source."
        }
        return selectedSources
    }

    private fun resolveSource(
        source: SqlConsoleSourceConfig,
        resolver: ValueResolver,
    ): ResolvedSqlConsoleShardConfig {
        return ResolvedSqlConsoleShardConfig(
            name = source.name.trim().also { require(it.isNotBlank()) { "Имя shard не должно быть пустым." } },
            jdbcUrl = resolver.resolve(source.jdbcUrl).trim().also {
                require(it.isNotBlank()) { "jdbcUrl для shard ${source.name} не должен быть пустым." }
            },
            username = resolver.resolve(source.username).trim().also {
                require(it.isNotBlank()) { "username для shard ${source.name} не должен быть пустым." }
            },
            password = resolver.resolve(source.password).trim().also {
                require(it.isNotBlank()) { "password для shard ${source.name} не должен быть пустым." }
            },
        )
    }
}

class JdbcShardSqlExecutor : ShardSqlExecutor {
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

private fun parseAndValidateStatement(rawSql: String): SqlConsoleStatement {
    val trimmed = rawSql.trim()
    require(trimmed.isNotBlank()) { "SQL-запрос не должен быть пустым." }

    val normalized = trimmed
        .replace(Regex("(?s)/\\*.*?\\*/"), " ")
        .lineSequence()
        .map { it.substringBefore("--") }
        .joinToString(" ")
        .trim()

    val withoutTrailingSemicolon = normalized.removeSuffix(";").trim()
    require(!withoutTrailingSemicolon.contains(";")) {
        "В SQL-консоли разрешен только один SQL-запрос за запуск."
    }

    val leadingKeyword = withoutTrailingSemicolon
        .substringBefore(" ")
        .ifBlank { withoutTrailingSemicolon }
        .uppercase()

    return SqlConsoleStatement(sql = trimmed, leadingKeyword = leadingKeyword)
}
