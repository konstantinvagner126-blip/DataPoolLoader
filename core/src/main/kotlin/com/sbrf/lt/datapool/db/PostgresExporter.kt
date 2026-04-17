package com.sbrf.lt.datapool.db

import com.sbrf.lt.datapool.app.SourceExportFinishedEvent
import com.sbrf.lt.datapool.app.SourceExportProgressEvent
import com.sbrf.lt.datapool.app.SourceExportStartedEvent
import com.sbrf.lt.datapool.app.port.SourceExporter
import com.sbrf.lt.datapool.export.CsvSupport
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.datapool.model.ExportTask
import com.sbrf.lt.datapool.model.SourceExecutionResult
import org.apache.commons.csv.CSVPrinter
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement
import java.time.Instant

class PostgresExporter(
    private val connectionProvider: (String, String, String) -> Connection = { jdbcUrl, username, password ->
        DriverManager.getConnection(jdbcUrl, username, password)
    },
) : SourceExporter {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun export(task: ExportTask): SourceExecutionResult {
        val startedAt = Instant.now()
        logger.info("Запуск выгрузки для источника {}", task.source.name)
        task.executionListener.onEvent(
            SourceExportStartedEvent(
                timestamp = startedAt,
                sourceName = task.source.name,
            )
        )

        return try {
            connectionProvider(task.resolvedJdbcUrl, task.resolvedUsername, task.resolvedPassword).use { connection ->
                connection.autoCommit = false
                connection.prepareStatement(
                    task.sql,
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY,
                ).use { statement ->
                    configureStreaming(connection, statement, task.fetchSize)
                    statement.queryTimeout = task.queryTimeoutSec ?: 0
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
                                    logProgress(task, rowCount)
                                }
                                printer.flush()
                                val finishedAt = Instant.now()
                                if (rowCount == 0L) {
                                    logger.info("Выгрузка источника {} завершена. Получено 0 строк", task.source.name)
                                } else {
                                    logger.info("Выгрузка источника {} завершена. Получено {} строк", task.source.name, rowCount)
                                }
                                val result = SourceExecutionResult(
                                    sourceName = task.source.name,
                                    status = ExecutionStatus.SUCCESS,
                                    rowCount = rowCount,
                                    outputFile = task.outputFile,
                                    columns = columns,
                                    startedAt = startedAt,
                                    finishedAt = finishedAt,
                                )
                                task.executionListener.onEvent(
                                    SourceExportFinishedEvent(
                                        timestamp = finishedAt,
                                        sourceName = task.source.name,
                                        status = result.status,
                                        rowCount = result.rowCount,
                                        columns = result.columns,
                                        outputFile = result.outputFile?.toString(),
                                    )
                                )
                                result
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            logger.error("Ошибка выгрузки для источника {}: {}", task.source.name, ex.message, ex)
            val failedAt = Instant.now()
            val result = SourceExecutionResult(
                sourceName = task.source.name,
                status = ExecutionStatus.FAILED,
                rowCount = 0,
                outputFile = null,
                columns = emptyList(),
                startedAt = startedAt,
                finishedAt = failedAt,
                errorMessage = ex.message ?: "Неизвестная ошибка",
            )
            task.executionListener.onEvent(
                SourceExportFinishedEvent(
                    timestamp = failedAt,
                    sourceName = task.source.name,
                    status = result.status,
                    rowCount = result.rowCount,
                    columns = result.columns,
                    outputFile = null,
                    errorMessage = result.errorMessage,
                )
            )
            result
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

    private fun logProgress(task: ExportTask, rowCount: Long) {
        if (rowCount % task.progressLogEveryRows == 0L) {
            logger.info("Выгрузка источника {}: обработано {} строк", task.source.name, rowCount)
            task.executionListener.onEvent(
                SourceExportProgressEvent(
                    timestamp = Instant.now(),
                    sourceName = task.source.name,
                    rowCount = rowCount,
                )
            )
        }
    }
}
