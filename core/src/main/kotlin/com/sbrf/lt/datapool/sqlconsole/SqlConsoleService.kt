package com.sbrf.lt.datapool.sqlconsole

import com.fasterxml.jackson.annotation.JsonAlias
import com.sbrf.lt.datapool.config.ValueResolver
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

data class SqlConsoleConfig(
    val fetchSize: Int = 1000,
    val maxRowsPerShard: Int = 200,
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

fun interface ShardSqlExecutor {
    fun execute(
        shard: ResolvedSqlConsoleShardConfig,
        statement: SqlConsoleStatement,
        fetchSize: Int,
        maxRows: Int,
    ): RawShardExecutionResult
}

data class SqlConsoleInfo(
    val configured: Boolean,
    val sourceNames: List<String>,
    val maxRowsPerShard: Int,
)

data class SqlConsoleQueryResult(
    val sql: String,
    val statementType: SqlConsoleStatementType,
    val statementKeyword: String,
    val shardResults: List<RawShardExecutionResult>,
    val maxRowsPerShard: Int,
)

class SqlConsoleService(
    private val config: SqlConsoleConfig,
    private val executor: ShardSqlExecutor = JdbcShardSqlExecutor(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun info(): SqlConsoleInfo = SqlConsoleInfo(
        configured = config.sources.isNotEmpty(),
        sourceNames = config.sources.map { it.name },
        maxRowsPerShard = config.maxRowsPerShard,
    )

    fun executeQuery(
        rawSql: String,
        credentialsPath: Path?,
        selectedSourceNames: List<String> = emptyList(),
    ): SqlConsoleQueryResult {
        val statement = parseAndValidateStatement(rawSql)
        require(config.fetchSize > 0) { "Параметр ui.sqlConsole.fetchSize должен быть больше 0." }
        require(config.maxRowsPerShard > 0) { "Параметр ui.sqlConsole.maxRowsPerShard должен быть больше 0." }
        require(config.sources.isNotEmpty()) {
            "В конфиге UI не настроены source-подключения. Заполни ui.sqlConsole.sources."
        }
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

        val resolver = ValueResolver.fromFile(credentialsPath)
        val resolvedShards = selectedSources.map { shard ->
            ResolvedSqlConsoleShardConfig(
                name = shard.name.trim().also { require(it.isNotBlank()) { "Имя shard не должно быть пустым." } },
                jdbcUrl = resolver.resolve(shard.jdbcUrl).trim().also {
                    require(it.isNotBlank()) { "jdbcUrl для shard ${shard.name} не должен быть пустым." }
                },
                username = resolver.resolve(shard.username).trim().also {
                    require(it.isNotBlank()) { "username для shard ${shard.name} не должен быть пустым." }
                },
                password = resolver.resolve(shard.password).trim().also {
                    require(it.isNotBlank()) { "password для shard ${shard.name} не должен быть пустым." }
                },
            )
        }

        val shardResults = resolvedShards.map { shard ->
            runCatching {
                executor.execute(
                    shard = shard,
                    statement = statement,
                    fetchSize = config.fetchSize,
                    maxRows = config.maxRowsPerShard,
                )
            }.onFailure { ex ->
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

    private fun determineResultType(results: List<RawShardExecutionResult>): SqlConsoleStatementType {
        return if (results.any { it.columns.isNotEmpty() || it.rows.isNotEmpty() }) {
            SqlConsoleStatementType.RESULT_SET
        } else {
            SqlConsoleStatementType.COMMAND
        }
    }
}

class JdbcShardSqlExecutor : ShardSqlExecutor {
    override fun execute(
        shard: ResolvedSqlConsoleShardConfig,
        statement: SqlConsoleStatement,
        fetchSize: Int,
        maxRows: Int,
    ): RawShardExecutionResult {
        DriverManager.getConnection(shard.jdbcUrl, shard.username, shard.password).use { connection ->
            return executeSql(connection, shard, statement, fetchSize, maxRows)
        }
    }

    private fun executeSql(
        connection: Connection,
        shard: ResolvedSqlConsoleShardConfig,
        statement: SqlConsoleStatement,
        fetchSize: Int,
        maxRows: Int,
    ): RawShardExecutionResult {
        connection.createStatement().use { jdbcStatement ->
            jdbcStatement.fetchSize = fetchSize
            jdbcStatement.maxRows = maxRows + 1
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
        }
    }
}

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
