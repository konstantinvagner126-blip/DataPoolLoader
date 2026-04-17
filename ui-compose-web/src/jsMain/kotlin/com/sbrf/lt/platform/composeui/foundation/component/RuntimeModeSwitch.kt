package com.sbrf.lt.platform.composeui.foundation.component

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
fun RuntimeModeSwitch(
    checked: Boolean,
    disabled: Boolean,
    onToggle: () -> Unit,
) {
    Label(attrs = {
        classes("runtime-mode-switch")
        attr("aria-label", "Переключатель режима UI")
    }) {
        Span({ classes("runtime-mode-switch-label") }) { Text("Файлы") }
        Input(
            type = InputType.Checkbox,
            attrs = {
                if (checked) {
                    attr("checked", "checked")
                }
                if (disabled) {
                    disabled()
                }
                onClick {
                    if (!disabled) {
                        onToggle()
                    }
                }
            },
        )
        Span({ classes("runtime-mode-switch-track") })
        Span({ classes("runtime-mode-switch-label") }) { Text("База данных") }
    }
}
