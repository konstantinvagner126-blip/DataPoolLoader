package com.sbrf.lt.datapool.db

import com.sbrf.lt.datapool.app.port.TargetImporter
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.datapool.model.TargetConfig
import com.sbrf.lt.datapool.model.TargetLoadSummary
import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

class PostgresImporter(
    private val connectionProvider: (String, String, String) -> Connection = { jdbcUrl, username, password ->
        DriverManager.getConnection(jdbcUrl, username, password)
    },
    private val copyExecutor: (Connection, String, Path) -> Long = { connection, copySql, mergedFile ->
        Files.newInputStream(mergedFile).use { input ->
            connection.unwrap(PGConnection::class.java)
                .copyAPI
                .copyIn(copySql, input)
        }
    },
) : TargetImporter {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val safeIdentifier = Regex("[A-Za-z_][A-Za-z0-9_]*")

    override fun importCsv(
        target: TargetConfig,
        resolvedJdbcUrl: String,
        resolvedUsername: String,
        mergedFile: Path,
        columns: List<String>,
        expectedRowCount: Long,
        resolvedPassword: String,
    ): TargetLoadSummary {
        val startedAt = Instant.now()
        logger.info("Запуск загрузки в целевую таблицу {}", target.table)
        logger.info("Подготовлено к загрузке в целевую таблицу {}: {} строк", target.table, expectedRowCount)

        return try {
            connectionProvider(resolvedJdbcUrl, resolvedUsername, resolvedPassword).use { connection ->
                connection.autoCommit = false
                try {
                    if (target.truncateBeforeLoad) {
                        val truncateSql = buildTruncateSql(target.table)
                        logger.info("Очистка целевой таблицы {} перед загрузкой", target.table)
                        connection.createStatement().use { statement ->
                            statement.execute(truncateSql)
                        }
                    }

                    val copySql = buildCopySql(target.table, columns)
                    val rows = copyExecutor(connection, copySql, mergedFile)
                    connection.commit()

                    logger.info(
                        "Загрузка в целевую таблицу {} завершена. Загружено {} строк",
                        target.table,
                        rows,
                    )
                    TargetLoadSummary(
                        table = target.table,
                        status = ExecutionStatus.SUCCESS,
                        rowCount = rows,
                        finishedAt = Instant.now(),
                        enabled = true,
                    )
                } catch (ex: Exception) {
                    connection.rollback()
                    throw ex
                }
            }
        } catch (ex: Exception) {
            logger.error("Ошибка загрузки в целевую таблицу {}: {}", target.table, ex.message, ex)
            TargetLoadSummary(
                table = target.table,
                status = ExecutionStatus.FAILED,
                rowCount = expectedRowCount,
                finishedAt = Instant.now(),
                enabled = true,
                errorMessage = ex.message ?: "Неизвестная ошибка",
            )
        }
    }

    internal fun buildCopySql(table: String, columns: List<String>): String {
        require(table.isNotBlank()) { "Имя целевой таблицы не должно быть пустым." }
        require(columns.isNotEmpty()) { "Для загрузки нужна хотя бы одна колонка." }

        val qualifiedTable = table.split('.').joinToString(".") { quoteIdentifier(it) }
        val joinedColumns = columns.joinToString(", ") { quoteIdentifier(it) }
        return "COPY $qualifiedTable ($joinedColumns) FROM STDIN WITH (FORMAT csv, HEADER true)"
    }

    internal fun buildTruncateSql(table: String): String {
        require(table.isNotBlank()) { "Имя целевой таблицы не должно быть пустым." }
        val qualifiedTable = table.split('.').joinToString(".") { quoteIdentifier(it) }
        return "TRUNCATE TABLE $qualifiedTable"
    }

    private fun quoteIdentifier(identifier: String): String {
        require(safeIdentifier.matches(identifier)) {
            "Неподдерживаемый идентификатор '$identifier'. Используйте простые PostgreSQL-идентификаторы без пробелов и специальных символов."
        }
        return "\"$identifier\""
    }
}
