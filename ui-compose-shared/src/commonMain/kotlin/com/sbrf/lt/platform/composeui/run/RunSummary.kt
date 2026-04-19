package com.sbrf.lt.platform.composeui.run

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

private val summaryJsonCodec = Json {
    ignoreUnknownKeys = true
}

data class StructuredRunSummary(
    val mergeMode: String? = null,
    val parallelism: Int? = null,
    val fetchSize: Int? = null,
    val queryTimeoutSec: Int? = null,
    val progressLogEveryRows: Long? = null,
    val mergedFile: String? = null,
    val mergedRowCount: Long? = null,
    val maxMergedRows: Long? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val targetEnabled: Boolean? = null,
    val targetStatus: String? = null,
    val targetTable: String? = null,
    val targetRowCount: Long? = null,
    val targetErrorMessage: String? = null,
    val successfulSourcesCount: Int = 0,
    val failedSourcesCount: Int = 0,
    val allocations: List<SummarySourceAllocation> = emptyList(),
    val failedSources: List<SummaryFailedSource> = emptyList(),
)

data class SummarySourceAllocation(
    val sourceName: String,
    val availableRows: Long? = null,
    val mergedRows: Long? = null,
    val mergedPercent: Double? = null,
)

data class SummaryFailedSource(
    val sourceName: String,
    val status: String? = null,
    val rowCount: Long? = null,
    val errorMessage: String? = null,
)

fun parseStructuredRunSummary(summaryJson: String?): StructuredRunSummary? {
    if (summaryJson.isNullOrBlank() || summaryJson == "{}") {
        return null
    }
    return runCatching {
        val root = summaryJsonCodec.parseToJsonElement(summaryJson).jsonObject
        val targetLoad = root["targetLoad"]?.asObject()
        val mergeDetails = root["mergeDetails"]?.asObject()
        val allocations = mergeDetails?.get("sourceAllocations")?.asArray().orEmpty().mapNotNull { item ->
            val obj = item.asObject() ?: return@mapNotNull null
            SummarySourceAllocation(
                sourceName = obj.string("sourceName") ?: return@mapNotNull null,
                availableRows = obj.long("availableRows"),
                mergedRows = obj.long("mergedRows"),
                mergedPercent = obj.double("mergedPercent"),
            )
        }
        val failedSources = root["failedSources"]?.asArray().orEmpty().mapNotNull { item ->
            val obj = item.asObject() ?: return@mapNotNull null
            SummaryFailedSource(
                sourceName = obj.string("sourceName") ?: return@mapNotNull null,
                status = obj.string("status"),
                rowCount = obj.long("rowCount"),
                errorMessage = obj.string("errorMessage"),
            )
        }
        StructuredRunSummary(
            mergeMode = root.string("mergeMode"),
            parallelism = root.int("parallelism"),
            fetchSize = root.int("fetchSize"),
            queryTimeoutSec = root.int("queryTimeoutSec"),
            progressLogEveryRows = root.long("progressLogEveryRows"),
            mergedFile = root.string("mergedFile"),
            mergedRowCount = root.long("mergedRowCount"),
            maxMergedRows = root.long("maxMergedRows"),
            startedAt = root.string("startedAt"),
            finishedAt = root.string("finishedAt"),
            targetEnabled = root.boolean("targetEnabled"),
            targetStatus = targetLoad?.string("status"),
            targetTable = targetLoad?.string("table"),
            targetRowCount = targetLoad?.long("rowCount"),
            targetErrorMessage = targetLoad?.string("errorMessage"),
            successfulSourcesCount = root["successfulSources"]?.asArray()?.size ?: 0,
            failedSourcesCount = failedSources.size,
            allocations = allocations,
            failedSources = failedSources,
        )
    }.getOrNull()
}

private fun JsonElement.asObject(): JsonObject? =
    this as? JsonObject

private fun JsonElement.asArray(): JsonArray? =
    this as? JsonArray

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

private fun JsonObject.double(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

private fun JsonObject.boolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
