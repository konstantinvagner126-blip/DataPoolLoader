package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun EditorActionButton(
    label: String,
    enabled: Boolean,
    style: EditorActionStyle = EditorActionStyle.SecondaryOutline,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", style.cssClass)
        if (!enabled) {
            disabled()
        }
        attr("type", "button")
        if (enabled) {
            onClick { onClick() }
        }
    }) {
        Text(label)
    }
}

@Composable
internal fun EditorIconActionButton(
    icon: String,
    label: String,
    title: String,
    enabled: Boolean,
    style: EditorActionStyle = EditorActionStyle.SecondaryOutline,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", "module-editor-icon-btn", style.cssClass)
        attr("type", "button")
        attr("title", title)
        attr("aria-label", title)
        if (!enabled) {
            disabled()
        }
        if (enabled) {
            onClick { onClick() }
        }
    }) {
        Span({ classes("module-editor-icon-btn-icon") }) { Text(icon) }
        Span({ classes("module-editor-icon-btn-label") }) { Text(label) }
    }
}

internal enum class EditorActionStyle(
    val cssClass: String,
) {
    PrimarySolid("btn-primary"),
    Success("btn-success"),
    PrimaryOutline("btn-outline-primary"),
    SecondaryOutline("btn-outline-secondary"),
    DangerOutline("btn-outline-danger"),
}
