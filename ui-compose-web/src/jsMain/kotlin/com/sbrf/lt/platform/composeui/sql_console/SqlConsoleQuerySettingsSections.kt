package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleQuerySettingsBlock(
    state: SqlConsolePageState,
    onStrictSafetyToggle: () -> Unit,
    onAutoCommitToggle: (Boolean) -> Unit,
) {
    SqlConsoleSettingToggle(
        label = "Read-only",
        checked = state.strictSafetyEnabled,
        onToggle = onStrictSafetyToggle,
    )
    SqlConsoleSettingToggle(
        label = "Autocommit",
        checked = state.transactionMode == "AUTO_COMMIT",
        onToggle = { onAutoCommitToggle(state.transactionMode != "AUTO_COMMIT") },
    )
}

@Composable
private fun SqlConsoleSettingToggle(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Div({ classes("sql-query-library-block") }) {
        Label(attrs = { classes("d-flex", "align-items-center", "gap-2", "small", "text-secondary", "mb-0") }) {
            Input(type = InputType.Checkbox, attrs = {
                classes("form-check-input")
                if (checked) {
                    attr("checked", "checked")
                }
                onClick { onToggle() }
            })
            Span { Text(label) }
        }
    }
}
