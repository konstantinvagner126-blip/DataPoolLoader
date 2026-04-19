package com.sbrf.lt.platform.composeui.run

import com.sbrf.lt.platform.composeui.module_runs.CompactProgressEntry
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunDetailsResponse
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunEventResponse
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunSourceResultResponse
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunSummaryResponse

fun translateRunStatus(status: String?): String =
    when (status?.uppercase()) {
        "RUNNING" -> "Выполняется"
        "SUCCESS" -> "Успешно"
        "SUCCESS_WITH_WARNINGS" -> "Успешно с предупреждениями"
        "FAILED" -> "Ошибка"
        "SKIPPED" -> "Пропущено"
        "PENDING" -> "Ожидание"
        "NOT_ENABLED" -> "Отключено"
        else -> status ?: "-"
    }

fun translateLaunchSource(kind: String?): String =
    when (kind?.uppercase()) {
        "WORKING_COPY" -> "Личный черновик"
        "CURRENT_REVISION" -> "Текущая ревизия"
        else -> kind ?: "-"
    }

fun translateStage(stage: String?): String =
    when (stage?.uppercase()) {
        "PREPARE" -> "Подготовка"
        "SOURCE" -> "Источники"
        "MERGE" -> "Объединение"
        "TARGET" -> "Загрузка в целевую таблицу"
        "RUN" -> "Завершение"
        else -> stage ?: "-"
    }

fun translateStageKey(stageKey: String): String =
    when (stageKey) {
        "sources" -> "Источники"
        "merge" -> "Объединение"
        "target" -> "Загрузка"
        "finish" -> "Завершение"
        else -> "Подготовка"
    }

fun translateArtifactKind(kind: String?): String =
    when (kind?.uppercase()) {
        "SOURCE_OUTPUT" -> "CSV источника"
        "MERGED_OUTPUT" -> "Итоговый merged.csv"
        "SUMMARY_JSON" -> "Файл summary.json"
        else -> kind ?: "-"
    }

fun translateArtifactStatus(status: String?): String =
    when (status?.uppercase()) {
        "PRESENT" -> "Доступен"
        "DELETED" -> "Удален"
        "MISSING" -> "Не найден"
        else -> status ?: "-"
    }

fun artifactStatusTone(status: String?): String =
    when (status?.uppercase()) {
        "PRESENT" -> "success"
        "DELETED" -> "warning"
        else -> "danger"
    }

fun summarizeSourceCounters(run: ModuleRunSummaryResponse): String {
    val success = run.successfulSourceCount?.toString() ?: "-"
    val failed = run.failedSourceCount?.toString() ?: "-"
    val skipped = run.skippedSourceCount?.toString() ?: "-"
    return "Успешных: $success · ошибок: $failed · пропущено: $skipped"
}

fun detectRunStageKey(
    run: ModuleRunSummaryResponse,
    events: List<ModuleRunEventResponse>,
): String {
    val normalizedStatus = run.status.uppercase()
    if (normalizedStatus == "SUCCESS" || normalizedStatus == "SUCCESS_WITH_WARNINGS") {
        return "finish"
    }
    val stageSequence = events
        .asReversed()
        .mapNotNull { event -> mapStageToKey(event.stage) }
    val lastOperationalStage = stageSequence.firstOrNull { it != "finish" }
    val lastStage = lastOperationalStage ?: stageSequence.firstOrNull()
    if (lastStage != null) {
        return lastStage
    }
    if (!run.targetTableName.isNullOrBlank() || !run.targetStatus.isNullOrBlank()) {
        return if (normalizedStatus == "FAILED") "target" else "prepare"
    }
    return if (normalizedStatus == "FAILED") "finish" else "prepare"
}

fun detectActiveSourceName(
    run: ModuleRunSummaryResponse,
    sourceResults: List<ModuleRunSourceResultResponse>,
    events: List<ModuleRunEventResponse>,
): String? {
    if (!run.status.equals("RUNNING", ignoreCase = true)) {
        return null
    }
    sourceResults.firstOrNull { it.status.equals("RUNNING", ignoreCase = true) }
        ?.sourceName
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    return events
        .asReversed()
        .firstOrNull { event ->
            event.sourceName != null &&
                (event.eventType.equals("SOURCE_PROGRESS", ignoreCase = true) ||
                    event.eventType.equals("SOURCE_STARTED", ignoreCase = true))
        }
        ?.sourceName
        ?.takeIf { it.isNotBlank() }
}

fun isCompactProgressEvent(event: ModuleRunEventResponse): Boolean =
    when (event.eventType.uppercase()) {
        "RUN_CREATED",
        "RUN_STARTED",
        "SOURCE_STARTED",
        "MERGE_STARTED",
        "TARGET_STARTED",
        "RUNSTARTEDEVENT",
        "SOURCEEXPORTSTARTEDEVENT",
        "MERGESTARTEDEVENT",
        "TARGETIMPORTSTARTEDEVENT",
        "OUTPUTCLEANUPEVENT" -> false
        else -> true
    }

fun buildCompactProgressEntries(
    details: ModuleRunDetailsResponse,
    limit: Int = 5,
): List<CompactProgressEntry> {
    val eventEntries = details.events
        .filter(::isCompactProgressEvent)
        .asReversed()
        .map { event ->
            CompactProgressEntry(
                timestamp = event.timestamp,
                message = event.message?.takeIf { it.isNotBlank() } ?: event.eventType,
                severity = event.severity,
            )
        }

    val syntheticProgressEntries = details.sourceResults
        .filter { details.run.status.equals("RUNNING", ignoreCase = true) }
        .filter { it.status.equals("RUNNING", ignoreCase = true) }
        .filter { (it.exportedRowCount ?: 0L) > 0L }
        .sortedWith(
            compareByDescending<ModuleRunSourceResultResponse> { it.exportedRowCount ?: 0L }
                .thenBy { it.sortOrder },
        )
        .map { source ->
            CompactProgressEntry(
                message = "Источник ${source.sourceName}: выгружено ${source.exportedRowCount ?: 0L} строк.",
                severity = "INFO",
            )
        }
        .filterNot { synthetic ->
            eventEntries.any { event -> event.message == synthetic.message }
        }

    return (syntheticProgressEntries + eventEntries)
        .take(limit)
}

private fun mapStageToKey(stage: String?): String? =
    when (stage?.uppercase()) {
        "PREPARE" -> "prepare"
        "SOURCE" -> "sources"
        "MERGE" -> "merge"
        "TARGET" -> "target"
        "RUN" -> "finish"
        else -> null
    }
