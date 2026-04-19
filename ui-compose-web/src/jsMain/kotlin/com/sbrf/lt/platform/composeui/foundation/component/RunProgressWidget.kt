package com.sbrf.lt.platform.composeui.foundation.component

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.dom.classesFromString
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

data class RunProgressStage(
    val key: String,
    val label: String,
    val status: String,
)

data class RunProgressMetric(
    val label: String,
    val value: String,
    val tone: String = "default",
)

private val runProgressDefinitions = listOf(
    "prepare" to "Подготовка",
    "sources" to "Источники",
    "merge" to "Объединение",
    "target" to "Загрузка",
    "finish" to "Завершение",
)

fun buildRunProgressStages(
    currentStageKey: String,
    overallStatus: String?,
): List<RunProgressStage> {
    val normalizedStageKey = runProgressDefinitions.firstOrNull { it.first == currentStageKey }?.first ?: "prepare"
    val normalizedStatus = overallStatus?.uppercase() ?: "PENDING"
    val currentIndex = runProgressDefinitions.indexOfFirst { it.first == normalizedStageKey }.coerceAtLeast(0)

    return runProgressDefinitions.mapIndexed { index, (key, label) ->
        val stageStatus = when {
            normalizedStatus == "SUCCESS" || normalizedStatus == "SUCCESS_WITH_WARNINGS" -> "success"
            normalizedStatus == "FAILED" && index < currentIndex -> "success"
            normalizedStatus == "FAILED" && index == currentIndex -> "failed"
            normalizedStatus == "RUNNING" && index < currentIndex -> "success"
            normalizedStatus == "RUNNING" && index == currentIndex -> "active"
            index == currentIndex -> "active"
            else -> "pending"
        }
        RunProgressStage(
            key = key,
            label = label,
            status = stageStatus,
        )
    }
}

@Composable
fun RunProgressWidget(
    title: String,
    subtitle: String,
    statusLabel: String,
    statusClassName: String,
    running: Boolean,
    stages: List<RunProgressStage>,
    metrics: List<RunProgressMetric>,
    showStatus: Boolean = true,
) {
    Div({ classes("run-progress-widget") }) {
        Div({ classes("run-progress-widget-head") }) {
            Div {
                Div({ classes("run-progress-title") }) { Text(title) }
                Div({ classes("run-progress-subtitle") }) { Text(subtitle) }
            }
            if (showStatus) {
                Div({ classes("run-progress-status-wrap") }) {
                    Span({
                        classes("run-progress-indicator")
                        if (running) {
                            classes("run-progress-indicator-running")
                        }
                        attr("aria-hidden", "true")
                    })
                    if (running) {
                        Div({
                            classes("run-progress-spinner-arrows")
                            attr("aria-hidden", "true")
                        }) {
                            Span({ classes("run-progress-spinner-arrow", "run-progress-spinner-arrow-forward") }) {
                                Text("↻")
                            }
                            Span({ classes("run-progress-spinner-arrow", "run-progress-spinner-arrow-backward") }) {
                                Text("↺")
                            }
                        }
                    }
                    Span({ classesFromString(statusClassName) }) {
                        Text(statusLabel)
                    }
                }
            }
        }

        Div({ classes("run-progress-stage-track") }) {
            stages.forEach { stage ->
                Div({ classes("run-progress-stage", "run-progress-stage-${stage.status}") }) {
                    Span({
                        classes("run-progress-stage-marker")
                        attr("aria-hidden", "true")
                    })
                    Span({ classes("run-progress-stage-label") }) {
                        Text(stage.label)
                    }
                }
            }
        }

        if (metrics.isNotEmpty()) {
            Div({ classes("run-progress-metrics") }) {
                metrics.forEach { metric ->
                    Div({ classes("run-progress-metric", "run-progress-metric-${metric.tone}") }) {
                        Div({ classes("run-progress-metric-label") }) {
                            Text(metric.label)
                        }
                        Div({ classes("run-progress-metric-value") }) {
                            Text(metric.value)
                        }
                    }
                }
            }
        }
    }
}
