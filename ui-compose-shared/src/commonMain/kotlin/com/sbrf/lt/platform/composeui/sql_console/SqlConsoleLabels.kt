package com.sbrf.lt.platform.composeui.sql_console

import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse

data class SqlConsoleContextSelectionPill(
    val label: String,
    val value: String,
    val tone: String,
)

fun buildConsoleInfoText(info: SqlConsoleInfo?): String =
    when {
        info == null -> "Конфигурация не загружена."
        !info.configured -> "SQL-консоль не настроена. Проверь конфигурацию источников и credential.properties."
        info.groups.none { !it.synthetic } -> "Доступно источников: ${info.sourceCatalog.size}. Лимит строк по умолчанию: ${info.maxRowsPerShard}."
        else -> "Доступно источников: ${info.sourceCatalog.size}, групп: ${info.groups.count { !it.synthetic }}. Лимит строк по умолчанию: ${info.maxRowsPerShard}."
    }

fun buildConnectionCheckStatusText(result: SqlConsoleConnectionCheckResponse): String {
    val success = result.sourceResults.count { it.status.equals("SUCCESS", ignoreCase = true) || it.status.equals("OK", ignoreCase = true) }
    val failed = result.sourceResults.size - success
    return "Проверка подключений завершена. Успешно: $success, с ошибкой: $failed."
}

fun translateConnectionStatus(status: String): String =
    when {
        status.equals("SUCCESS", ignoreCase = true) || status.equals("OK", ignoreCase = true) -> "Доступен"
        status.equals("FAILED", ignoreCase = true) || status.equals("ERROR", ignoreCase = true) -> "Недоступен"
        status.equals("RUNNING", ignoreCase = true) -> "Проверяется"
        else -> status
    }

fun sourceStatusTone(status: SqlConsoleSourceConnectionStatus?): String =
    when {
        status == null -> "unknown"
        status.status.equals("SUCCESS", ignoreCase = true) || status.status.equals("OK", ignoreCase = true) -> "success"
        status.status.equals("FAILED", ignoreCase = true) || status.status.equals("ERROR", ignoreCase = true) -> "failed"
        else -> "unknown"
    }

fun translateSourceStatus(status: String): String =
    when {
        status.equals("SUCCESS", ignoreCase = true) || status.equals("OK", ignoreCase = true) -> "Успешно"
        status.equals("FAILED", ignoreCase = true) || status.equals("ERROR", ignoreCase = true) -> "Ошибка"
        status.equals("RUNNING", ignoreCase = true) -> "Выполняется"
        status.equals("SUCCESS_WITH_WARNINGS", ignoreCase = true) -> "С предупреждениями"
        else -> status
    }

fun runButtonTone(
    analysis: SqlStatementAnalysis,
    strictSafetyEnabled: Boolean,
): String =
    when {
        analysis.keyword == "SQL" -> "primary"
        strictSafetyEnabled && !analysis.readOnly -> "danger"
        analysis.dangerous -> "danger"
        !analysis.readOnly -> "warning"
        else -> "primary"
    }

fun sourceStatusSuffix(status: String): String =
    when {
        status.equals("SUCCESS", ignoreCase = true) || status.equals("OK", ignoreCase = true) -> "success"
        status.equals("FAILED", ignoreCase = true) || status.equals("ERROR", ignoreCase = true) -> "failed"
        status.equals("RUNNING", ignoreCase = true) -> "running"
        status.equals("SUCCESS_WITH_WARNINGS", ignoreCase = true) -> "success_with_warnings"
        else -> "not_enabled"
    }

fun sourceStatusBadgeTone(status: String): String =
    when {
        status.equals("SUCCESS", ignoreCase = true) || status.equals("OK", ignoreCase = true) -> "success"
        status.equals("FAILED", ignoreCase = true) || status.equals("ERROR", ignoreCase = true) -> "danger"
        status.equals("RUNNING", ignoreCase = true) -> "primary"
        status.equals("SUCCESS_WITH_WARNINGS", ignoreCase = true) -> "warning"
        else -> "secondary"
    }

fun buildCredentialsStatusText(status: CredentialsStatusResponse): String {
    val sourceLabel = when {
        status.uploaded -> "загружен через UI"
        status.mode.equals("FILE", ignoreCase = true) -> "файл по умолчанию"
        else -> "файл не задан"
    }
    val availability = if (status.fileAvailable) "доступен" else "не найден"
    return "$sourceLabel: ${status.displayName} ($availability)"
}

fun buildCredentialsStatusBadgeText(status: CredentialsStatusResponse): String =
    when {
        status.uploaded -> "загружен через UI"
        status.mode.equals("FILE", ignoreCase = true) && status.fileAvailable -> "файл по умолчанию"
        status.mode.equals("FILE", ignoreCase = true) -> "файл не найден"
        else -> "не задан"
    }

fun buildSelectedGroupsSummary(
    info: SqlConsoleInfo?,
    selectedGroupNames: List<String>,
    selectedSourceNames: List<String>,
): String {
    if (selectedSourceNames.isEmpty()) {
        return "не выбраны"
    }
    val explicitGroupNames = info.explicitGroupNames()
    val normalizedSelectedGroups = selectedGroupNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filter { explicitGroupNames.isEmpty() || it in explicitGroupNames }
        .distinct()
    return when {
        normalizedSelectedGroups.isEmpty() -> "без групп"
        else -> summarizeContextNames(normalizedSelectedGroups)
    }
}

fun buildSelectedSourcesSummary(selectedSourceNames: List<String>): String {
    val normalizedSelectedSources = selectedSourceNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    return when {
        normalizedSelectedSources.isEmpty() -> "не выбраны"
        normalizedSelectedSources.size <= 2 -> normalizedSelectedSources.joinToString(", ")
        else -> "${normalizedSelectedSources.size} ${buildCountWord(normalizedSelectedSources.size, "источник", "источника", "источников")}"
    }
}

fun buildSelectedContextPills(
    info: SqlConsoleInfo?,
    selectedGroupNames: List<String>,
    manuallyIncludedSourceNames: List<String>,
    manuallyExcludedSourceNames: List<String>,
): List<SqlConsoleContextSelectionPill> {
    val pills = mutableListOf<SqlConsoleContextSelectionPill>()
    val normalizedSelectedGroups = selectedGroupNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    normalizedSelectedGroups.forEach { groupName ->
        pills += SqlConsoleContextSelectionPill(
            label = "Группа",
            value = groupName,
            tone = "primary",
        )
    }

    val groupedSourceNames = info.explicitGroupedSourceNames()
    val normalizedManualInclusions = manuallyIncludedSourceNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    val (ungroupedSources, manuallySelectedGroupedSources) = normalizedManualInclusions.partition { it !in groupedSourceNames }
    if (manuallySelectedGroupedSources.isNotEmpty()) {
        pills += SqlConsoleContextSelectionPill(
            label = "Вручную",
            value = summarizeContextNames(manuallySelectedGroupedSources),
            tone = "neutral",
        )
    }
    if (ungroupedSources.isNotEmpty()) {
        pills += SqlConsoleContextSelectionPill(
            label = "Без группы",
            value = summarizeContextNames(ungroupedSources),
            tone = "neutral",
        )
    }

    val normalizedManualExclusions = manuallyExcludedSourceNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    if (normalizedManualExclusions.isNotEmpty()) {
        pills += SqlConsoleContextSelectionPill(
            label = "Исключено",
            value = summarizeContextNames(normalizedManualExclusions),
            tone = "warning",
        )
    }
    return pills
}

fun translateTransactionMode(mode: String): String =
    when {
        mode.equals("AUTO_COMMIT", ignoreCase = true) -> "Autocommit"
        mode.equals("TRANSACTION_PER_SHARD", ignoreCase = true) -> "Транзакция по source"
        else -> mode
    }

private fun SqlConsoleInfo?.explicitGroupNames(): Set<String> =
    this?.groups.orEmpty()
        .filterNot { it.synthetic }
        .map { it.name.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

private fun SqlConsoleInfo?.explicitGroupedSourceNames(): Set<String> =
    this?.groups.orEmpty()
        .filterNot { it.synthetic }
        .flatMap { it.sources }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

private fun summarizeContextNames(values: List<String>): String =
    when {
        values.isEmpty() -> ""
        values.size <= 2 -> values.joinToString(", ")
        else -> values.take(2).joinToString(", ") + " +" + (values.size - 2)
    }

private fun buildCountWord(
    count: Int,
    singular: String,
    paucal: String,
    plural: String,
): String {
    val normalizedCount = count % 100
    val lastDigit = count % 10
    return when {
        normalizedCount in 11..14 -> plural
        lastDigit == 1 -> singular
        lastDigit in 2..4 -> paucal
        else -> plural
    }
}
