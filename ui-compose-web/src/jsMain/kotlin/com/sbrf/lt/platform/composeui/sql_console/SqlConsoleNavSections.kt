package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleNavActionButton(
    label: String,
    hrefValue: String? = null,
    active: Boolean = false,
) {
    if (active) {
        Button(attrs = {
            classes("btn", "btn-dark")
            attr("type", "button")
            disabled()
        }) {
            Text(label)
        }
        return
    }
    A(attrs = {
        classes("btn", "btn-outline-secondary")
        href(hrefValue ?: "#")
    }) {
        Text(label)
    }
}
