package com.sbrf.lt.platform.composeui.module_runs

import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.format.formatNumber
import kotlinx.serialization.json.Json

internal val technicalDiagnosticsJson = Json {
    prettyPrint = true
}

internal fun formatStageDuration(
    events: List<ModuleRunEventResponse>,
    stage: String,
    running: Boolean,
): String {
    val stageEvents = events.filter { it.stage.equals(stage, ignoreCase = true) }
    val startedAt = stageEvents
        .firstOrNull { isStageStartEvent(it) }
        ?.timestamp
        ?: stageEvents.firstOrNull()?.timestamp
    val finishedAt = stageEvents
        .lastOrNull { isStageFinishedEvent(it) }
        ?.timestamp
    return formatDuration(startedAt, finishedAt, running = running)
}

private fun isStageStartEvent(event: ModuleRunEventResponse): Boolean =
    event.eventType.uppercase().contains("STARTED")

private fun isStageFinishedEvent(event: ModuleRunEventResponse): Boolean =
    event.eventType.uppercase().contains("FINISHED") ||
        event.eventType.uppercase().contains("FAILED")

internal fun formatTimeoutSeconds(value: Int?): String =
    when (value) {
        null -> "Не задан"
        else -> "${value}с"
    }

internal fun formatRowsInterval(value: Long?): String =
    when (value) {
        null -> "-"
        else -> "${formatNumber(value)} строк"
    }

internal fun formatBooleanFlag(value: Boolean?): String =
    when (value) {
        true -> "Да"
        false -> "Нет"
        null -> "-"
    }

internal fun buildBackHref(route: ModuleRunsRouteState): String =
    if (route.storage == "database") {
        "/db-modules?module=${route.moduleId}"
    } else {
        "/modules?module=${route.moduleId}"
    }

internal fun extractArtifactName(
    filePath: String,
    artifactKey: String,
): String {
    val normalized = filePath.replace("\\", "/")
    val candidate = normalized.substringAfterLast("/", "")
    return candidate.ifBlank { artifactKey.ifBlank { "-" } }
}
