package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun WorkspaceActionButton(
    label: String,
    toneClass: String,
    disabled: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", toneClass)
        if (small) {
            classes("btn-sm")
        }
        attr("type", "button")
        if (disabled) {
            disabled()
        }
        onClick { onClick() }
    }) { Text(label) }
}
