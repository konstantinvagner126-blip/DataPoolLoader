package com.example.datapoolloader.merge

import com.example.datapoolloader.export.CsvSupport
import com.example.datapoolloader.model.MergeMode
import com.example.datapoolloader.model.SourceExecutionResult
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path

class MergeService {
    fun merge(
        successfulSources: List<SourceExecutionResult>,
        mergeMode: MergeMode,
        outputFile: Path,
    ): Long {
        require(successfulSources.isNotEmpty()) { "No successful sources available for merge." }

        Files.newBufferedWriter(outputFile).use { writer ->
            CSVPrinter(writer, CsvSupport.formatWithoutAutoHeader).use { printer ->
                printer.printRecord(successfulSources.first().columns)
                val mergedRows = when (mergeMode) {
                    MergeMode.PLAIN -> mergePlain(successfulSources, printer)
                    MergeMode.ROUND_ROBIN -> mergeRoundRobin(successfulSources, printer)
                }
                printer.flush()
                return mergedRows
            }
        }
    }

    private fun mergePlain(sources: List<SourceExecutionResult>, printer: CSVPrinter): Long {
        var total = 0L
        sources.forEach { source ->
            openParser(source.outputFile!!).use { parser ->
                parser.forEach { record ->
                    printer.printRecord(record.toList())
                    total++
                }
            }
        }
        return total
    }

    private fun mergeRoundRobin(sources: List<SourceExecutionResult>, printer: CSVPrinter): Long {
        val readers = sources.map { source ->
            SourceReader(source.sourceName, openParser(source.outputFile!!))
        }.toMutableList()
        var total = 0L

        try {
            while (readers.isNotEmpty()) {
                val iterator = readers.iterator()
                while (iterator.hasNext()) {
                    val reader = iterator.next()
                    val record = reader.nextRecord()
                    if (record == null) {
                        reader.close()
                        iterator.remove()
                    } else {
                        printer.printRecord(record)
                        total++
                    }
                }
            }
        } finally {
            readers.forEach { it.close() }
        }

        return total
    }

    private fun openParser(file: Path): CSVParser {
        val bufferedReader: BufferedReader = Files.newBufferedReader(file)
        return CsvSupport.formatWithoutAutoHeader.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .get()
            .parse(bufferedReader)
    }
}

private class SourceReader(
    private val sourceName: String,
    private val parser: CSVParser,
) : AutoCloseable {
    private val iterator = parser.iterator()

    fun nextRecord(): List<String>? {
        if (!iterator.hasNext()) {
            return null
        }
        return iterator.next().toList()
    }

    override fun close() {
        parser.close()
    }
}
