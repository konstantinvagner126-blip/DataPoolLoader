package com.sbrf.lt.platform.composeui.foundation.run

import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.format.formatNumber
import com.sbrf.lt.platform.composeui.module_runs.ModuleRunEventResponse

fun formatStageDuration(
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

fun formatTimeoutSeconds(value: Int?): String =
    when (value) {
        null -> "Не задан"
        else -> "${value}с"
    }

fun formatRowsInterval(value: Long?): String =
    when (value) {
        null -> "-"
        else -> "${formatNumber(value)} строк"
    }

fun formatBooleanFlag(value: Boolean?): String =
    when (value) {
        true -> "Да"
        false -> "Нет"
        null -> "-"
    }

fun extractArtifactName(
    filePath: String,
    artifactKey: String,
): String {
    val normalized = filePath.replace("\\", "/")
    val candidate = normalized.substringAfterLast("/", "")
    return candidate.ifBlank { artifactKey.ifBlank { "-" } }
}

private fun isStageStartEvent(event: ModuleRunEventResponse): Boolean =
    event.eventType.uppercase().contains("STARTED")

private fun isStageFinishedEvent(event: ModuleRunEventResponse): Boolean =
    event.eventType.uppercase().contains("FINISHED") ||
        event.eventType.uppercase().contains("FAILED")
