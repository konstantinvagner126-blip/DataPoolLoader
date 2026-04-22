package com.sbrf.lt.platform.composeui.module_runs

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.component.StatusBadge
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.run.extractArtifactName
import com.sbrf.lt.platform.composeui.run.artifactStatusTone
import com.sbrf.lt.platform.composeui.run.formatFileSizeValue
import com.sbrf.lt.platform.composeui.run.translateArtifactKind
import com.sbrf.lt.platform.composeui.run.translateArtifactStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

private val technicalDiagnosticsJson = Json {
    prettyPrint = true
}

@Composable
internal fun TechnicalDiagnosticsSection(
    details: ModuleRunDetailsResponse,
    showTechnicalDiagnostics: Boolean,
    enabled: Boolean,
    onToggleTechnicalDiagnostics: () -> Unit,
) {
    if (!enabled) {
        return
    }
    SectionCard(
        title = "Техническая диагностика",
        actions = {
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                onClick { onToggleTechnicalDiagnostics() }
            }) {
                Text("Показать / скрыть")
            }
        },
    ) {
        if (showTechnicalDiagnostics) {
            Pre({ classes("event-log", "technical-log", "mb-0") }) {
                Text(
                    if (details.events.isEmpty()) {
                        "Технические события пока недоступны."
                    } else {
                        details.events
                            .takeLast(200)
                            .joinToString("\n\n") { event ->
                                technicalDiagnosticsJson.encodeToString(
                                    ModuleRunEventResponse.serializer(),
                                    event,
                                )
                            }
                    },
                )
            }
        } else {
            P({ classes("text-secondary", "mb-0") }) {
                Text("Технические события скрыты.")
            }
        }
    }
}

@Composable
internal fun RunArtifactsSection(details: ModuleRunDetailsResponse) {
    Div({
        attr("id", "run-artifacts-section")
    }) {
        SectionCard(
            title = "Результаты запуска",
            subtitle = "Итоговые артефакты выбранного запуска.",
        ) {
            if (details.artifacts.isEmpty()) {
                P({ classes("text-secondary", "mb-0") }) { Text("Результаты запуска пока недоступны.") }
            } else {
                Div({ classes("run-artifact-grid") }) {
                    details.artifacts.forEach { item ->
                        ArtifactCard(item)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RawSummaryJsonSection(
    details: ModuleRunDetailsResponse,
    history: ModuleRunHistoryResponse,
) {
    Div({
        attr("id", "run-summary-json-section")
    }) {
        SectionCard(
            title = "summary.json",
            subtitle = "Raw-представление итогового summary.",
        ) {
            if (history.uiSettings.showRawSummaryJson) {
                Pre({
                    classes("small", "mb-0", "bg-light", "border", "rounded-3", "p-3")
                }) {
                    Text(details.summaryJson ?: "{}")
                }
            } else {
                P({ classes("text-secondary", "mb-0") }) {
                    Text("Показ raw summary отключен в пользовательских настройках UI.")
                }
            }
        }
    }
}

@Composable
internal fun ArtifactCard(item: ModuleRunArtifactResponse) {
    Div({ classes("run-artifact-card") }) {
        Div({ classes("run-artifact-kind") }) {
            Text(translateArtifactKind(item.artifactKind))
        }
        Div({ classes("run-artifact-title") }) {
            Text(extractArtifactName(item.filePath, item.artifactKey))
        }
        Div({ classes("d-flex", "flex-wrap", "align-items-center", "gap-2") }) {
            StatusBadge(
                text = translateArtifactStatus(item.storageStatus),
                tone = artifactStatusTone(item.storageStatus),
            )
            Span({ classes("run-artifact-note") }) {
                Text(formatFileSizeValue(item.fileSizeBytes))
            }
        }
        Div({ classes("run-artifact-note") }) {
            Text("Ключ: ${item.artifactKey.ifBlank { "-" }}")
        }
        Div({ classes("run-artifact-path") }) {
            Code {
                Text(item.filePath)
            }
        }
    }
}
