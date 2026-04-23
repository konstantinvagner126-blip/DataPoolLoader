package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleWorkingContextStrip(
    state: SqlConsolePageState,
    uiState: SqlConsolePageUiState,
) {
    val selectionPills = buildSelectedContextPills(
        info = state.info,
        selectedGroupNames = state.selectedGroupNames,
        manuallyIncludedSourceNames = state.manuallyIncludedSourceNames,
        manuallyExcludedSourceNames = state.manuallyExcludedSourceNames,
    )
    Div({ classes("sql-context-strip") }) {
        Div({ classes("sql-context-strip-head") }) {
            Div({ classes("sql-context-strip-title") }) { Text("Текущий контекст выполнения") }
            Div({ classes("sql-context-strip-note") }) {
                Text("Режимы и выбранные source показаны здесь, чтобы не искать их в sidebar перед запуском.")
            }
        }
        Div({ classes("sql-context-chip-row") }) {
            SqlConsoleContextChip(
                label = "Группы",
                value = buildSelectedGroupsSummary(
                    info = state.info,
                    selectedGroupNames = state.selectedGroupNames,
                    selectedSourceNames = state.selectedSourceNames,
                ),
                tone = when {
                    state.selectedSourceNames.isEmpty() -> "warning"
                    state.selectedGroupNames.isEmpty() -> "neutral"
                    else -> "primary"
                },
            )
            SqlConsoleContextChip(
                label = "Источники",
                value = buildSelectedSourcesSummary(state.selectedSourceNames),
                tone = if (state.selectedSourceNames.isEmpty()) "warning" else "primary",
            )
            SqlConsoleContextChip(
                label = "Режим",
                value = translateTransactionMode(state.transactionMode),
                tone = if (state.transactionMode == "AUTO_COMMIT") "neutral" else "warning",
            )
            SqlConsoleContextChip(
                label = "Защита",
                value = if (state.strictSafetyEnabled) "строгая read-only" else "разрешены изменения",
                tone = if (state.strictSafetyEnabled) "success" else "warning",
            )
            SqlConsoleContextChip(
                label = "Страница",
                value = "${state.pageSize} строк",
                tone = "neutral",
            )
            SqlConsoleContextChip(
                label = "Credentials",
                value = uiState.credentialsStatus?.let(::buildCredentialsStatusBadgeText) ?: "статус загружается",
                tone = credentialsStatusTone(uiState.credentialsStatus),
            )
        }
        if (selectionPills.isNotEmpty()) {
            Div({ classes("sql-context-selection-row") }) {
                selectionPills.forEach { pill ->
                    SqlConsoleContextSelectionPillView(pill)
                }
            }
        }
    }
}

@Composable
private fun SqlConsoleContextChip(
    label: String,
    value: String,
    tone: String,
) {
    Div({ classes("sql-context-chip", "sql-context-chip-$tone") }) {
        Span({ classes("sql-context-chip-label") }) { Text(label) }
        Span({ classes("sql-context-chip-value") }) { Text(value) }
    }
}

@Composable
private fun SqlConsoleContextSelectionPillView(
    pill: SqlConsoleContextSelectionPill,
) {
    Div({ classes("sql-context-selection-pill", "sql-context-selection-pill-${pill.tone}") }) {
        Span({ classes("sql-context-selection-pill-label") }) { Text(pill.label) }
        Span({ classes("sql-context-selection-pill-value") }) { Text(pill.value) }
    }
}

private fun credentialsStatusTone(status: com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse?): String =
    when {
        status == null -> "neutral"
        status.uploaded || status.fileAvailable -> "success"
        else -> "warning"
    }
