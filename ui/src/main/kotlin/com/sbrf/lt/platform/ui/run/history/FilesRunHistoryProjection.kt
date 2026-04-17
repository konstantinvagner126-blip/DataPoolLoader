package com.sbrf.lt.platform.ui.run.history

import com.sbrf.lt.platform.ui.model.ModuleRunArtifactResponse
import com.sbrf.lt.platform.ui.model.ModuleRunEventResponse
import com.sbrf.lt.platform.ui.model.ModuleRunSourceResultResponse
import com.sbrf.lt.platform.ui.model.UiRunSnapshot
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.fileSize

internal fun projectFilesRunSourceResults(run: UiRunSnapshot): List<ModuleRunSourceResultResponse> {
    val states = linkedMapOf<String, FilesSourceState>()
    var sortOrder = 0

    run.events.forEach { event ->
        val sourceName = event.stringValue("sourceName") ?: return@forEach
        val state = states.getOrPut(sourceName) { FilesSourceState(sortOrder = sortOrder++) }
        when (detectFilesEventType(event)) {
            "SourceExportStartedEvent" -> {
                state.startedAt = event.instantValue("timestamp")
                state.status = "RUNNING"
            }
            "SourceExportProgressEvent" -> {
                state.exportedRowCount = event.longValue("rowCount") ?: state.exportedRowCount
                if (state.status == "PENDING") {
                    state.status = "RUNNING"
                }
            }
            "SourceExportFinishedEvent" -> {
                state.finishedAt = event.instantValue("timestamp")
                state.status = event.statusValue() ?: state.status
                state.exportedRowCount = event.longValue("rowCount") ?: state.exportedRowCount
                state.errorMessage = event.stringValue("errorMessage")
            }
            "SourceSchemaMismatchEvent" -> {
                state.finishedAt = event.instantValue("timestamp")
                state.status = "SKIPPED"
                state.errorMessage = "Источник исключен из объединения из-за несовпадения схемы."
            }
            "MergeFinishedEvent" -> {
                state.mergedRowCount = event.longMap("sourceCounts")[sourceName] ?: state.mergedRowCount
            }
        }
    }

    return states.entries.map { (sourceName, state) ->
        ModuleRunSourceResultResponse(
            sourceName = sourceName,
            sortOrder = state.sortOrder,
            status = state.status,
            startedAt = state.startedAt,
            finishedAt = state.finishedAt,
            exportedRowCount = state.exportedRowCount,
            mergedRowCount = state.mergedRowCount,
            errorMessage = state.errorMessage,
        )
    }
}

internal fun projectFilesRunArtifacts(
    run: UiRunSnapshot,
    sourceResults: List<ModuleRunSourceResultResponse>,
): List<ModuleRunArtifactResponse> {
    val outputDir = run.outputDir ?: return emptyList()
    val artifacts = mutableListOf<ModuleRunArtifactResponse>()

    artifacts += createArtifact(
        artifactKind = "MERGED_OUTPUT",
        artifactKey = "merged",
        filePath = joinOutputPath(outputDir, "merged.csv"),
    )

    if (!run.summaryJson.isNullOrBlank()) {
        artifacts += createArtifact(
            artifactKind = "SUMMARY_JSON",
            artifactKey = "summary",
            filePath = joinOutputPath(outputDir, "summary.json"),
        )
    }

    sourceResults
        .filter { it.status == "SUCCESS" }
        .sortedBy { it.sortOrder }
        .forEach { source ->
            artifacts += createArtifact(
                artifactKind = "SOURCE_OUTPUT",
                artifactKey = source.sourceName,
                filePath = joinOutputPath(outputDir, "${source.sourceName}.csv"),
            )
        }

    return artifacts
}

internal fun projectFilesRunEvents(run: UiRunSnapshot): List<ModuleRunEventResponse> =
    run.events.mapIndexedNotNull { index, event ->
        val eventType = detectFilesEventType(event) ?: return@mapIndexedNotNull null
        ModuleRunEventResponse(
            seqNo = index + 1,
            timestamp = event.instantValue("timestamp"),
            stage = filesStageFor(eventType),
            eventType = eventType,
            severity = filesSeverityFor(eventType, event),
            sourceName = event.stringValue("sourceName"),
            message = filesMessageFor(eventType, event),
            payload = event,
        )
    }

private fun createArtifact(
    artifactKind: String,
    artifactKey: String,
    filePath: String,
): ModuleRunArtifactResponse {
    val path = runCatching { Path.of(filePath) }.getOrNull()
    val exists = path?.let(Files::exists) == true
    return ModuleRunArtifactResponse(
        artifactKind = artifactKind,
        artifactKey = artifactKey,
        filePath = filePath,
        storageStatus = if (exists) "PRESENT" else "MISSING",
        fileSizeBytes = path?.takeIf(Files::exists)?.fileSize(),
    )
}

private fun joinOutputPath(outputDir: String, fileName: String): String {
    val base = outputDir.trim()
    if (base.isEmpty()) {
        return fileName
    }
    val separator = if (base.contains('\\') && !base.contains('/')) "\\" else "/"
    return "${base.trimEnd('/', '\\')}$separator$fileName"
}

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

private fun filesStageFor(eventType: String): String =
    when (eventType) {
        "RunStartedEvent" -> "PREPARE"
        "SourceExportStartedEvent",
        "SourceExportProgressEvent",
        "SourceExportFinishedEvent",
        "SourceSchemaMismatchEvent" -> "SOURCE"
        "MergeStartedEvent", "MergeFinishedEvent" -> "MERGE"
        "TargetImportStartedEvent", "TargetImportFinishedEvent" -> "TARGET"
        else -> "RUN"
    }

private fun filesSeverityFor(eventType: String, event: Map<String, Any?>): String =
    when (eventType) {
        "SourceSchemaMismatchEvent" -> "WARNING"
        "SourceExportFinishedEvent",
        "TargetImportFinishedEvent",
        "RunFinishedEvent" -> {
            when (event.statusValue()) {
                "FAILED" -> "ERROR"
                "SUCCESS" -> "SUCCESS"
                "SKIPPED" -> "INFO"
                else -> "INFO"
            }
        }
        "MergeFinishedEvent" -> "SUCCESS"
        else -> "INFO"
    }

private fun filesMessageFor(eventType: String, event: Map<String, Any?>): String =
    when (eventType) {
        "RunStartedEvent" -> {
            val sourceCount = event.stringList("sourceNames").size
            val mergeMode = event.stringValue("mergeMode") ?: "-"
            "Запуск начат. Источников: $sourceCount, режим объединения: $mergeMode."
        }
        "SourceExportStartedEvent" -> "Начата выгрузка из источника ${event.stringValue("sourceName")}."
        "SourceExportProgressEvent" -> "Источник ${event.stringValue("sourceName")}: выгружено ${event.longValue("rowCount") ?: 0} строк."
        "SourceExportFinishedEvent" -> {
            if (event.statusValue() == "SUCCESS") {
                "Источник ${event.stringValue("sourceName")} завершен успешно. Получено ${event.longValue("rowCount") ?: 0} строк."
            } else {
                "Источник ${event.stringValue("sourceName")} завершился с ошибкой: ${event.stringValue("errorMessage") ?: "неизвестная ошибка"}."
            }
        }
        "SourceSchemaMismatchEvent" -> "Источник ${event.stringValue("sourceName")} исключен из объединения: набор колонок отличается от базового."
        "MergeStartedEvent" -> "Начато объединение данных из ${event.stringList("sourceNames").size} успешных источников."
        "MergeFinishedEvent" -> "Объединение завершено. В merged.csv записано ${event.longValue("rowCount") ?: 0} строк."
        "TargetImportStartedEvent" -> "Начата загрузка merged.csv в таблицу ${event.stringValue("table")}."
        "TargetImportFinishedEvent" -> {
            when (event.statusValue()) {
                "SUCCESS" -> "Загрузка в таблицу ${event.stringValue("table")} завершена. Загружено ${event.longValue("rowCount") ?: 0} строк."
                "SKIPPED" -> "Загрузка в target пропущена."
                else -> "Загрузка в таблицу ${event.stringValue("table")} завершилась ошибкой: ${event.stringValue("errorMessage") ?: "неизвестная ошибка"}."
            }
        }
        "OutputCleanupEvent" -> "Удален временный файл ${event.stringValue("fileName")}."
        "RunFinishedEvent" -> {
            if (event.statusValue() == "SUCCESS") {
                "Запуск завершен успешно."
            } else {
                "Запуск завершен с ошибкой: ${event.stringValue("errorMessage") ?: "неизвестная ошибка"}."
            }
        }
        else -> event.toString()
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
    stringValue(key)?.let {
        runCatching { Instant.parse(it) }.getOrNull()
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

private class FilesSourceState(
    val sortOrder: Int,
    var status: String = "PENDING",
    var startedAt: Instant? = null,
    var finishedAt: Instant? = null,
    var exportedRowCount: Long? = null,
    var mergedRowCount: Long? = null,
    var errorMessage: String? = null,
)
