package com.example.datapoolloader.db

import com.example.datapoolloader.export.CsvSupport
import com.example.datapoolloader.model.ExecutionStatus
import com.example.datapoolloader.model.ExportTask
import com.example.datapoolloader.model.SourceExecutionResult
import org.apache.commons.csv.CSVPrinter
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.time.Instant

class PostgresExporter {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun export(task: ExportTask): SourceExecutionResult {
        val startedAt = Instant.now()
        logger.info("Запуск выгрузки для источника {}", task.source.name)

        return try {
            DriverManager.getConnection(task.resolvedJdbcUrl, task.resolvedUsername, task.resolvedPassword).use { connection ->
                connection.autoCommit = false
                connection.prepareStatement(
                    task.sql,
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY,
                ).use { statement ->
                    configureStreaming(connection, statement, task.fetchSize)
                    statement.executeQuery().use { resultSet ->
                        val columns = readColumns(resultSet)
                        Files.newBufferedWriter(task.outputFile).use { writer ->
                            CSVPrinter(writer, CsvSupport.formatWithoutAutoHeader).use { printer ->
                                printer.printRecord(columns)
                                var rowCount = 0L
                                while (resultSet.next()) {
                                    val row = columns.indices.map { columnIndex ->
                                        resultSet.getObject(columnIndex + 1)
                                    }
                                    printer.printRecord(row)
                                    rowCount++
                                    logProgress(task.source.name, rowCount, task.progressLogEveryRows)
                                }
                                printer.flush()
                                val finishedAt = Instant.now()
                                if (rowCount == 0L) {
                                    logger.info("Выгрузка источника {} завершена. Получено 0 строк", task.source.name)
                                } else {
                                    logger.info("Выгрузка источника {} завершена. Получено {} строк", task.source.name, rowCount)
                                }
                                SourceExecutionResult(
                                    sourceName = task.source.name,
                                    status = ExecutionStatus.SUCCESS,
                                    rowCount = rowCount,
                                    outputFile = task.outputFile,
                                    columns = columns,
                                    startedAt = startedAt,
                                    finishedAt = finishedAt,
                                )
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            logger.error("Ошибка выгрузки для источника {}: {}", task.source.name, ex.message, ex)
            SourceExecutionResult(
                sourceName = task.source.name,
                status = ExecutionStatus.FAILED,
                rowCount = 0,
                outputFile = null,
                columns = emptyList(),
                startedAt = startedAt,
                finishedAt = Instant.now(),
                errorMessage = ex.message ?: "Неизвестная ошибка",
            )
        }
    }

    private fun configureStreaming(connection: Connection, statement: Statement, fetchSize: Int) {
        connection.autoCommit = false
        statement.fetchSize = fetchSize
    }

    private fun readColumns(resultSet: ResultSet): List<String> {
        val metaData = resultSet.metaData
        return (1..metaData.columnCount).map { metaData.getColumnLabel(it) }
    }

    private fun logProgress(sourceName: String, rowCount: Long, interval: Long) {
        if (rowCount % interval == 0L) {
            logger.info("Выгрузка источника {}: обработано {} строк", sourceName, rowCount)
        }
    }
}
