package com.sbrf.lt.platform.composeui.module_sync

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.StatusBadge
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import org.jetbrains.compose.web.dom.Code
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SyncRunDetailsPanel(state: ModuleSyncPageState) {
    Div({ classes("panel", "h-100") }) {
        Div({ classes("panel-title") }) { Text("Детали импорта") }

        Div({ classes("mt-3") }) {
            val details = state.selectedRunDetails
            if (details == null) {
                EmptyStateCard("Детали импорта", "Выбери запуск из истории слева, чтобы посмотреть детали.")
                return@Div
            }

            val run = details.run
            ModuleSyncRunSummaryHeader(run)
            ModuleSyncRunSummaryList(run)

            if (details.items.isEmpty()) {
                Div({ classes("text-secondary", "small", "mt-3") }) {
                    Text("Детали по модулям появятся после завершения импорта.")
                }
                return@Div
            }

            Div({ classes("mt-3") }) {
                details.items.forEach { item ->
                    Div({ classes("card", "mb-3") }) {
                        Div({ classes("card-body", "py-3", "px-3") }) {
                            Div({ classes("d-flex", "flex-wrap", "align-items-center", "gap-2") }) {
                                Code({ classes("fw-semibold") }) { Text(item.moduleCode) }
                                StatusBadge(
                                    text = translateSyncAction(item.action),
                                    tone = syncActionTone(item.action),
                                )
                                StatusBadge(
                                    text = translateSyncStatus(item.status),
                                    tone = syncStatusTone(item.status),
                                )
                                if (!item.resultRevisionId.isNullOrBlank()) {
                                    Span({ classes("small", "text-secondary") }) { Text("Ревизия: ${item.resultRevisionId}") }
                                }
                            }
                            val errorMessage = item.errorMessage
                            if (!errorMessage.isNullOrBlank()) {
                                Div({ classes("small", "text-danger", "mt-2") }) { Text(errorMessage) }
                            }
                            val itemDetails = item.details
                            val reason = itemDetails?.get("reason")?.toString()?.trim('"')
                            val message = itemDetails?.get("message")?.toString()?.trim('"')
                            if (!reason.isNullOrBlank()) {
                                Div({ classes("small", "text-secondary", "mt-2") }) { Text("Причина: $reason") }
                            }
                            if (!message.isNullOrBlank()) {
                                Div({ classes("small", "text-secondary") }) { Text(message) }
                            }
                            if (itemDetails != null && itemDetails.isNotEmpty()) {
                                Pre({ classes("bg-body-tertiary", "rounded", "p-2", "mt-2", "small", "mb-0") }) {
                                    Text(itemDetails.toString())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleSyncRunSummaryHeader(run: ModuleSyncRunSummaryResponse) {
    Div({ classes("run-summary-header") }) {
        Div {
            Div({ classes("run-summary-title") }) {
                Text(syncRunTitle(run))
            }
            Div({ classes("run-summary-subtitle") }) {
                Text("${translateSyncStatus(run.status)} · ${formatDateTime(run.startedAt)}")
            }
        }
        Div({ classes("run-summary-metrics") }) {
            SyncMetricBadge("Область", translateSyncScope(run.scope))
            SyncMetricBadge("Обработано", run.totalProcessed.toString())
            SyncMetricBadge("Создано", run.totalCreated.toString())
            SyncMetricBadge("Обновлено", run.totalUpdated.toString())
            SyncMetricBadge("Пропущено", run.totalSkipped.toString())
            SyncMetricBadge("С ошибкой", run.totalFailed.toString())
        }
    }
}

@Composable
private fun ModuleSyncRunSummaryList(run: ModuleSyncRunSummaryResponse) {
    Div({ classes("run-summary-list") }) {
        SyncSummaryRow("Запуск", syncRunTitle(run))
        SyncSummaryRow("Инициатор", actorLabel(run.startedByActorDisplayName, run.startedByActorId) ?: "-")
        SyncSummaryRow("Старт", formatDateTime(run.startedAt))
        SyncSummaryRow("Завершение", run.finishedAt?.let(::formatDateTime) ?: "еще выполняется")
        SyncSummaryRow("Идентификатор", run.syncRunId)
    }
}
