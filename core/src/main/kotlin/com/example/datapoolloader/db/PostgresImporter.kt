package com.example.datapoolloader.db

import com.example.datapoolloader.model.ExecutionStatus
import com.example.datapoolloader.model.TargetConfig
import com.example.datapoolloader.model.TargetLoadSummary
import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Instant

class PostgresImporter {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val safeIdentifier = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun importCsv(
        target: TargetConfig,
        resolvedJdbcUrl: String,
        resolvedUsername: String,
        mergedFile: Path,
        columns: List<String>,
        expectedRowCount: Long,
        resolvedPassword: String,
    ): TargetLoadSummary {
        val startedAt = Instant.now()
        logger.info("Starting import into target table {}", target.table)

        return try {
            DriverManager.getConnection(resolvedJdbcUrl, resolvedUsername, resolvedPassword).use { connection ->
                if (target.truncateBeforeLoad) {
                    val truncateSql = buildTruncateSql(target.table)
                    logger.info("Truncating target table {} before import", target.table)
                    connection.createStatement().use { statement ->
                        statement.execute(truncateSql)
                    }
                }

                val copySql = buildCopySql(target.table, columns)
                val rows = Files.newInputStream(mergedFile).use { input ->
                    connection.unwrap(PGConnection::class.java)
                        .copyAPI
                        .copyIn(copySql, input)
                }

                logger.info(
                    "Finished import into target table {}. Loaded {} rows",
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
            }
        } catch (ex: Exception) {
            logger.error("Target import failed for table {}: {}", target.table, ex.message, ex)
            TargetLoadSummary(
                table = target.table,
                status = ExecutionStatus.FAILED,
                rowCount = expectedRowCount,
                finishedAt = Instant.now(),
                enabled = true,
                errorMessage = ex.message ?: ex.javaClass.simpleName,
            )
        }
    }

    internal fun buildCopySql(table: String, columns: List<String>): String {
        require(table.isNotBlank()) { "Target table must not be blank." }
        require(columns.isNotEmpty()) { "At least one column is required for import." }

        val qualifiedTable = table.split('.').joinToString(".") { quoteIdentifier(it) }
        val joinedColumns = columns.joinToString(", ") { quoteIdentifier(it) }
        return "COPY $qualifiedTable ($joinedColumns) FROM STDIN WITH (FORMAT csv, HEADER true)"
    }

    internal fun buildTruncateSql(table: String): String {
        require(table.isNotBlank()) { "Target table must not be blank." }
        val qualifiedTable = table.split('.').joinToString(".") { quoteIdentifier(it) }
        return "TRUNCATE TABLE $qualifiedTable"
    }

    private fun quoteIdentifier(identifier: String): String {
        require(safeIdentifier.matches(identifier)) {
            "Unsupported identifier '$identifier'. Use simple PostgreSQL identifiers without spaces or special symbols."
        }
        return "\"$identifier\""
    }
}
