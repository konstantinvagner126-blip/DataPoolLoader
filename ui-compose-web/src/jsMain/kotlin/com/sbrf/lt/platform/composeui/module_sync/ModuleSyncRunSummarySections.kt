package com.sbrf.lt.platform.composeui.module_sync

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SyncMetricBadge(
    label: String,
    value: String,
) {
    Div({ classes("run-summary-metric") }) {
        Div({ classes("run-summary-metric-label") }) { Text(label) }
        Div({ classes("run-summary-metric-value") }) { Text(value) }
    }
}

@Composable
internal fun SyncSummaryRow(
    label: String,
    value: String,
) {
    Div({ classes("run-summary-item") }) {
        Div({ classes("run-summary-label") }) { Text(label) }
        Div({ classes("run-summary-value") }) { Text(value) }
    }
}
