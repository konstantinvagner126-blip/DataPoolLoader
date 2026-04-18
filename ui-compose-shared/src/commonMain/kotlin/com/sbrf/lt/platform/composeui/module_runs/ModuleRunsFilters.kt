package com.sbrf.lt.platform.composeui.module_runs

enum class ModuleRunsHistoryFilter(
    val code: String,
    val label: String,
) {
    ALL("ALL", "Все"),
    RUNNING("RUNNING", "Активные"),
    SUCCESS("SUCCESS", "Успешные"),
    WARNINGS("WARNINGS", "С предупреждениями"),
    FAILED("FAILED", "С ошибкой"),
}

fun moduleRunsHistoryFilterFromCode(code: String?): ModuleRunsHistoryFilter =
    ModuleRunsHistoryFilter.values().firstOrNull { filter -> filter.code == code } ?: ModuleRunsHistoryFilter.ALL

fun filterRuns(
    runs: List<ModuleRunSummaryResponse>,
    historyFilter: ModuleRunsHistoryFilter,
    searchQuery: String,
): List<ModuleRunSummaryResponse> {
    val normalizedQuery = searchQuery.trim().lowercase()
    return runs
        .filter { run -> matchesHistoryFilter(run, historyFilter) }
        .filter { run -> matchesSearchQuery(run, normalizedQuery) }
}

private fun matchesHistoryFilter(
    run: ModuleRunSummaryResponse,
    historyFilter: ModuleRunsHistoryFilter,
): Boolean =
    when (historyFilter) {
        ModuleRunsHistoryFilter.ALL -> true
        ModuleRunsHistoryFilter.RUNNING -> run.status.equals("RUNNING", ignoreCase = true)
        ModuleRunsHistoryFilter.SUCCESS ->
            run.status.equals("SUCCESS", ignoreCase = true) ||
                run.status.equals("SUCCESS_WITH_WARNINGS", ignoreCase = true)
        ModuleRunsHistoryFilter.WARNINGS -> hasWarnings(run)
        ModuleRunsHistoryFilter.FAILED -> run.status.equals("FAILED", ignoreCase = true)
    }

private fun matchesSearchQuery(
    run: ModuleRunSummaryResponse,
    searchQuery: String,
): Boolean {
    if (searchQuery.isBlank()) {
        return true
    }
    val haystack = listOfNotNull(
        run.runId,
        run.moduleId,
        run.moduleTitle,
        run.outputDir,
        run.targetTableName,
        run.executionSnapshotId,
        run.launchSourceKind,
        run.launchSourceKind?.replace('_', ' '),
        run.status,
        run.targetStatus,
    ).joinToString(" ").lowercase()
    return haystack.contains(searchQuery)
}

private fun hasWarnings(run: ModuleRunSummaryResponse): Boolean {
    if (run.status.equals("SUCCESS_WITH_WARNINGS", ignoreCase = true)) {
        return true
    }
    if ((run.failedSourceCount ?: 0) > 0) {
        return true
    }
    if ((run.skippedSourceCount ?: 0) > 0) {
        return true
    }
    return run.targetStatus.equals("FAILED", ignoreCase = true) ||
        run.targetStatus.equals("SKIPPED", ignoreCase = true)
}
