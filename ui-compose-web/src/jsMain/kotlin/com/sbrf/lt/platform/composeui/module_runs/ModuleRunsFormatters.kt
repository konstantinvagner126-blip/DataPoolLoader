package com.sbrf.lt.platform.composeui.module_runs

import com.sbrf.lt.platform.composeui.foundation.format.formatNumber

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

fun runStatusCssClass(status: String?): String {
    val normalized = status?.trim()?.lowercase() ?: "pending"
    return when (normalized) {
        "success_with_warnings" -> "status-badge status-success_with_warnings"
        "not_enabled" -> "status-badge status-not_enabled"
        else -> "status-badge status-$normalized"
    }
}

fun eventEntryCssClass(severity: String?): String =
    when (severity?.uppercase()) {
        "SUCCESS" -> "human-log-entry human-log-entry-success"
        "ERROR" -> "human-log-entry human-log-entry-error"
        "WARNING" -> "human-log-entry human-log-entry-warning"
        else -> "human-log-entry"
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

fun formatPercent(value: Double?): String {
    if (value == null) {
        return "-"
    }
    return js("new Intl.NumberFormat('ru-RU', { minimumFractionDigits: 0, maximumFractionDigits: 2 }).format(value)") as String + "%"
}

fun formatFileSize(bytes: Long?): String {
    if (bytes == null) {
        return "-"
    }
    val size = bytes.toDouble()
    return when {
        size < 1024 -> "${bytes} B"
        size < 1024 * 1024 -> "${((size / 1024) * 10).toInt() / 10.0} KB"
        else -> "${((size / (1024 * 1024)) * 10).toInt() / 10.0} MB"
    }
}

fun summarizeSourceCounters(run: ModuleRunSummaryResponse): String {
    val success = formatNumber(run.successfulSourceCount)
    val failed = formatNumber(run.failedSourceCount)
    val skipped = formatNumber(run.skippedSourceCount)
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

private fun mapStageToKey(stage: String?): String? =
    when (stage?.uppercase()) {
        "PREPARE" -> "prepare"
        "SOURCE" -> "sources"
        "MERGE" -> "merge"
        "TARGET" -> "target"
        "RUN" -> "finish"
        else -> null
    }
