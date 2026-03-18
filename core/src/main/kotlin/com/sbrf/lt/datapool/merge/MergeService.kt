package com.sbrf.lt.datapool.merge

import com.sbrf.lt.datapool.export.CsvSupport
import com.sbrf.lt.datapool.model.AppConfig
import com.sbrf.lt.datapool.model.MergeResult
import com.sbrf.lt.datapool.model.MergeMode
import com.sbrf.lt.datapool.model.SourceExecutionResult
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path

class MergeService {
    private val maxOpenReaders = 64

    fun merge(
        successfulSources: List<SourceExecutionResult>,
        appConfig: AppConfig,
        outputFile: Path,
    ): MergeResult {
        require(successfulSources.isNotEmpty()) { "Нет успешных источников для объединения." }

        Files.newBufferedWriter(outputFile).use { writer ->
            CSVPrinter(writer, CsvSupport.formatWithoutAutoHeader).use { printer ->
                printer.printRecord(successfulSources.first().columns)
                val merged = when (appConfig.mergeMode) {
                    MergeMode.PLAIN -> mergePlain(successfulSources, printer, appConfig.maxMergedRows)
                    MergeMode.ROUND_ROBIN -> mergeRoundRobin(successfulSources, printer, appConfig.maxMergedRows)
                    MergeMode.PROPORTIONAL -> mergeWeighted(
                        successfulSources = successfulSources,
                        targetCounts = capTargetCounts(
                            successfulSources.associate { it.sourceName to it.rowCount },
                            appConfig.maxMergedRows,
                        ),
                        printer = printer,
                    )
                    MergeMode.QUOTA -> mergeWeighted(
                        successfulSources = successfulSources,
                        targetCounts = capTargetCounts(buildQuotaTargetCounts(successfulSources, appConfig), appConfig.maxMergedRows),
                        printer = printer,
                    )
                }
                printer.flush()
                return merged
            }
        }
    }

    private fun mergePlain(sources: List<SourceExecutionResult>, printer: CSVPrinter, maxRows: Long?): MergeResult {
        var total = 0L
        val counts = linkedMapOf<String, Long>()
        sources.forEach { source ->
            if (maxRows != null && total >= maxRows) return@forEach
            openParser(source.outputFile!!).use { parser ->
                val iterator = parser.iterator()
                while (iterator.hasNext()) {
                    if (maxRows != null && total >= maxRows) break
                    val record = iterator.next()
                    printer.printRecord(record.toList())
                    total++
                    counts[source.sourceName] = (counts[source.sourceName] ?: 0L) + 1
                }
            }
        }
        return MergeResult(total, counts)
    }

    private fun mergeRoundRobin(sources: List<SourceExecutionResult>, printer: CSVPrinter, maxRows: Long?): MergeResult {
        if (sources.size > maxOpenReaders) {
            return mergeRoundRobinWithReopen(sources, printer, maxRows)
        }

        val readers = sources.map { source ->
            SourceReader(source.sourceName, openParser(source.outputFile!!))
        }.toMutableList()
        var total = 0L
        val counts = linkedMapOf<String, Long>()

        try {
            while (readers.isNotEmpty()) {
                if (maxRows != null && total >= maxRows) break
                val iterator = readers.iterator()
                while (iterator.hasNext()) {
                    if (maxRows != null && total >= maxRows) break
                    val reader = iterator.next()
                    val record = reader.nextRecord()
                    if (record == null) {
                        reader.close()
                        iterator.remove()
                    } else {
                        printer.printRecord(record)
                        total++
                        counts[reader.sourceName()] = (counts[reader.sourceName()] ?: 0L) + 1
                    }
                }
            }
        } finally {
            readers.forEach { it.close() }
        }

        return MergeResult(total, counts)
    }

    private fun mergeRoundRobinWithReopen(sources: List<SourceExecutionResult>, printer: CSVPrinter, maxRows: Long?): MergeResult {
        val cursors = sources.map { ReopenableSourceCursor(it) }.toMutableList()
        var total = 0L
        val counts = linkedMapOf<String, Long>()

        while (cursors.isNotEmpty()) {
            if (maxRows != null && total >= maxRows) break
            val iterator = cursors.iterator()
            while (iterator.hasNext()) {
                if (maxRows != null && total >= maxRows) break
                val cursor = iterator.next()
                val record = cursor.nextRecord()
                if (record == null) {
                    iterator.remove()
                } else {
                    printer.printRecord(record)
                    total++
                    counts[cursor.sourceName()] = (counts[cursor.sourceName()] ?: 0L) + 1
                }
            }
        }

        return MergeResult(total, counts)
    }

    private fun mergeWeighted(
        successfulSources: List<SourceExecutionResult>,
        targetCounts: Map<String, Long>,
        printer: CSVPrinter,
    ): MergeResult {
        val activeSources = successfulSources.filter { (targetCounts[it.sourceName] ?: 0L) > 0L }
        if (activeSources.isEmpty()) {
            return MergeResult(0L, emptyMap())
        }

        val totalTarget = targetCounts.values.sum()
        val cursors = activeSources.associate { source ->
            val cursor = if (activeSources.size > maxOpenReaders) {
                ReopenableSourceCursor(source)
            } else {
                OpenSourceCursor(source)
            }
            source.sourceName to cursor
        }

        val scheduler = activeSources.map { source ->
            WeightedSourceState(
                sourceName = source.sourceName,
                targetCount = targetCounts.getValue(source.sourceName),
                stride = totalTarget.toDouble() / targetCounts.getValue(source.sourceName).toDouble(),
            )
        }.toMutableList()

        var total = 0L
        val counts = linkedMapOf<String, Long>()
        try {
            while (scheduler.isNotEmpty()) {
                val next = scheduler.minWithOrNull(compareBy<WeightedSourceState> { it.pass }.thenBy { it.order }) ?: break
                val cursor = cursors.getValue(next.sourceName)
                val record = cursor.nextRecord()
                if (record == null) {
                    scheduler.remove(next)
                    continue
                }

                printer.printRecord(record)
                total++
                counts[next.sourceName] = (counts[next.sourceName] ?: 0L) + 1
                next.emitted++
                next.pass += next.stride
                if (next.emitted >= next.targetCount) {
                    scheduler.remove(next)
                }
            }
        } finally {
            cursors.values.forEach { it.close() }
        }

        return MergeResult(total, counts)
    }

    private fun capTargetCounts(targetCounts: Map<String, Long>, maxMergedRows: Long?): Map<String, Long> {
        if (maxMergedRows == null) return targetCounts
        val total = targetCounts.values.sum()
        if (total <= maxMergedRows) return targetCounts
        if (maxMergedRows <= 0) return targetCounts.mapValues { 0L }

        val raw = targetCounts.map { (sourceName, count) ->
            val scaled = count.toDouble() * maxMergedRows.toDouble() / total.toDouble()
            ScaledCount(
                sourceName = sourceName,
                count = kotlin.math.floor(scaled).toLong(),
                fractional = scaled - kotlin.math.floor(scaled),
                maxAvailable = count,
            )
        }.toMutableList()

        var remaining = maxMergedRows - raw.sumOf { it.count }
        raw.sortedByDescending { it.fractional }.forEach { item ->
            if (remaining <= 0) return@forEach
            if (item.count < item.maxAvailable) {
                item.count++
                remaining--
            }
        }

        return raw.associate { it.sourceName to it.count }
    }

    private fun buildQuotaTargetCounts(
        successfulSources: List<SourceExecutionResult>,
        appConfig: AppConfig,
    ): Map<String, Long> {
        val successfulNames = successfulSources.map { it.sourceName }.toSet()
        val effectiveQuotas = appConfig.quotas
            .filter { it.source in successfulNames }
            .associate { it.source to it.percent }

        require(effectiveQuotas.isNotEmpty()) { "Для успешных источников не найдено ни одной квоты." }

        val quotaSum = effectiveQuotas.values.sum()
        val normalizedShares = effectiveQuotas.mapValues { (_, percent) -> percent / quotaSum }

        val totalTarget = successfulSources.minOf { source ->
            kotlin.math.floor(source.rowCount / normalizedShares.getValue(source.sourceName)).toLong()
        }

        val rawTargets = successfulSources.map { source ->
            val share = normalizedShares.getValue(source.sourceName)
            val raw = totalTarget * share
            PlannedCount(
                sourceName = source.sourceName,
                targetCount = kotlin.math.floor(raw).toLong(),
                fractional = raw - kotlin.math.floor(raw),
                available = source.rowCount,
            )
        }.toMutableList()

        var remaining = totalTarget - rawTargets.sumOf { it.targetCount }
        rawTargets.sortedByDescending { it.fractional }.forEach { planned ->
            if (remaining <= 0) return@forEach
            if (planned.targetCount < planned.available) {
                planned.targetCount++
                remaining--
            }
        }

        return rawTargets.associate { it.sourceName to it.targetCount }
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

private interface SourceCursor : AutoCloseable {
    fun nextRecord(): List<String>?
    fun sourceName(): String
}

private class SourceReader(
    private val sourceName: String,
    private val parser: CSVParser,
) : SourceCursor {
    private val iterator = parser.iterator()

    override fun nextRecord(): List<String>? {
        if (!iterator.hasNext()) {
            return null
        }
        return iterator.next().toList()
    }

    override fun close() {
        parser.close()
    }

    override fun sourceName(): String = sourceName
}

private class ReopenableSourceCursor(
    private val source: SourceExecutionResult,
) : SourceCursor {
    private var consumedRecords: Long = 0

    override fun nextRecord(): List<String>? {
        val parser = openParserInternal(source.outputFile!!)
        parser.use {
            val iterator = it.iterator()
            repeat(consumedRecords.toInt()) {
                if (iterator.hasNext()) {
                    iterator.next()
                }
            }
            if (!iterator.hasNext()) {
                return null
            }
            consumedRecords++
            return iterator.next().toList()
        }
    }

    override fun close() = Unit

    override fun sourceName(): String = source.sourceName
}

private fun openParserInternal(file: Path): CSVParser {
    val bufferedReader: BufferedReader = Files.newBufferedReader(file)
    return CsvSupport.formatWithoutAutoHeader.builder()
        .setHeader()
        .setSkipHeaderRecord(true)
        .get()
        .parse(bufferedReader)
}

private class OpenSourceCursor(
    source: SourceExecutionResult,
) : SourceCursor {
    private val delegate = SourceReader(source.sourceName, openParserInternal(source.outputFile!!))

    override fun nextRecord(): List<String>? = delegate.nextRecord()

    override fun close() = delegate.close()

    override fun sourceName(): String = delegate.sourceName()
}

private data class WeightedSourceState(
    val sourceName: String,
    val targetCount: Long,
    val stride: Double,
    val order: String = sourceName,
    var pass: Double = 0.0,
    var emitted: Long = 0,
)

private data class PlannedCount(
    val sourceName: String,
    var targetCount: Long,
    val fractional: Double,
    val available: Long,
)

private data class ScaledCount(
    val sourceName: String,
    var count: Long,
    val fractional: Double,
    val maxAvailable: Long,
)
