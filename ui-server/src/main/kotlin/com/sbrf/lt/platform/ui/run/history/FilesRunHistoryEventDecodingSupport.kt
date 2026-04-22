package com.sbrf.lt.platform.ui.run.history

import java.time.Instant
import kotlin.math.absoluteValue
import kotlin.math.floor

internal fun detectFilesEventType(event: Map<String, Any?>): String? {
    val explicit = event.stringValue("type")?.substringAfterLast('.')
    if (!explicit.isNullOrBlank()) {
        return explicit
    }
    return when {
        event.containsKey("summaryFile") && event.containsKey("mergedRowCount") -> "RunFinishedEvent"
        event.containsKey("fileName") && !event.containsKey("sourceName") -> "OutputCleanupEvent"
        event.containsKey("table") && event.containsKey("expectedRowCount") -> "TargetImportStartedEvent"
        event.containsKey("table") && event.containsKey("rowCount") && event.containsKey("status") -> "TargetImportFinishedEvent"
        event.containsKey("sourceCounts") -> "MergeFinishedEvent"
        event.containsKey("outputFile") && event.containsKey("sourceNames") -> "MergeStartedEvent"
        event.containsKey("expectedColumns") && event.containsKey("actualColumns") -> "SourceSchemaMismatchEvent"
        event.containsKey("sourceName") && event.containsKey("columns") && event.containsKey("status") -> "SourceExportFinishedEvent"
        event.containsKey("sourceName") && event.containsKey("rowCount") -> "SourceExportProgressEvent"
        event.containsKey("sourceName") -> "SourceExportStartedEvent"
        event.containsKey("mergeMode") && event.containsKey("targetEnabled") -> "RunStartedEvent"
        else -> null
    }
}

private fun Map<String, Any?>.stringValue(key: String): String? =
    this[key]?.toString()?.takeIf { it.isNotBlank() }

internal fun Map<String, Any?>.statusValue(): String? =
    stringValue("status")?.substringAfterLast('.')

private fun Map<String, Any?>.longValue(key: String): Long? =
    when (val value = this[key]) {
        null -> null
        is Number -> value.toLong()
        else -> value.toString().toLongOrNull()
    }

private fun Map<String, Any?>.instantValue(key: String): Instant? =
    when (val raw = this[key]) {
        null -> null
        is Number -> raw.toInstant()
        else -> raw.toString().takeIf { it.isNotBlank() }?.let { value ->
            runCatching { Instant.parse(value) }.getOrNull()
                ?: value.toDoubleOrNull()?.toInstant()
        }
    }

private fun Number.toInstant(): Instant {
    val numericValue = toDouble()
    return if (numericValue.absoluteValue >= 100_000_000_000.0) {
        Instant.ofEpochMilli(toLong())
    } else {
        val epochSeconds = floor(numericValue).toLong()
        val nanos = ((numericValue - epochSeconds) * 1_000_000_000.0).toLong()
        Instant.ofEpochSecond(epochSeconds, nanos)
    }
}

private fun Map<String, Any?>.stringList(key: String): List<String> =
    when (val value = this[key]) {
        is Iterable<*> -> value.mapNotNull { it?.toString() }
        else -> emptyList()
    }

private fun Map<String, Any?>.longMap(key: String): Map<String, Long> =
    when (val value = this[key]) {
        is Map<*, *> -> value.entries.mapNotNull { entry ->
            val mapKey = entry.key?.toString() ?: return@mapNotNull null
            val mapValue = when (val raw = entry.value) {
                is Number -> raw.toLong()
                else -> raw?.toString()?.toLongOrNull()
            } ?: return@mapNotNull null
            mapKey to mapValue
        }.toMap()
        else -> emptyMap()
    }

internal fun Map<String, Any?>.sourceCounts(): Map<String, Long> = longMap("sourceCounts")

private fun Map<String, Any?>.booleanValue(key: String): Boolean? =
    when (val value = this[key]) {
        null -> null
        is Boolean -> value
        else -> value.toString().toBooleanStrictOrNull()
    }

internal fun Map<String, Any?>.targetEnabledValue(): Boolean? = booleanValue("targetEnabled")

internal fun Map<String, Any?>.eventTimestamp(): Instant? = instantValue("timestamp")

internal fun Map<String, Any?>.eventSourceName(): String? = stringValue("sourceName")

internal fun Map<String, Any?>.eventRowCount(): Long? = longValue("rowCount")

internal fun Map<String, Any?>.eventErrorMessage(): String? = stringValue("errorMessage")

internal fun Map<String, Any?>.eventTableName(): String? = stringValue("table")

internal fun Map<String, Any?>.eventSourceNames(): List<String> = stringList("sourceNames")

internal fun Map<String, Any?>.eventStringValue(key: String): String? = stringValue(key)

internal fun Map<String, Any?>.eventLongValue(key: String): Long? = longValue(key)
