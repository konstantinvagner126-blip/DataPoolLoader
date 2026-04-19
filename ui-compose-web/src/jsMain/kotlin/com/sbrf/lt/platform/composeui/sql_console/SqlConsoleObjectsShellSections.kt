package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.dom.classesFromString
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun ObjectsNavActionButton(
    label: String,
    hrefValue: String? = null,
    active: Boolean = false,
) {
    if (active) {
        Button(attrs = {
            classes("btn", "btn-dark")
            attr("type", "button")
            disabled()
        }) { Text(label) }
        return
    }
    A(attrs = {
        classes("btn", "btn-outline-secondary")
        href(hrefValue ?: "#")
    }) { Text(label) }
}

@Composable
internal fun SqlObjectPanel(
    title: String,
    note: String? = null,
    panelClasses: String = "panel",
    useParagraphNote: Boolean = false,
    content: @Composable () -> Unit,
) {
    Div({ classesFromString(panelClasses) }) {
        Div({ classes("panel-title", "mb-2") }) { Text(title) }
        if (!note.isNullOrBlank()) {
            if (useParagraphNote) {
                P({ classes("small", "text-secondary", "mb-3") }) {
                    Text(note)
                }
            } else {
                Div({ classes("small", "text-secondary", "mb-3") }) {
                    Text(note)
                }
            }
        }
        content()
    }
}

@Composable
internal fun SqlObjectOverviewCard(
    label: String,
    value: String,
    note: String,
) {
    Div({ classes("sql-object-overview-card") }) {
        Div({ classes("eyebrow") }) { Text(label) }
        Div({ classes("sql-object-overview-value") }) { Text(value) }
        Div({ classes("small", "text-secondary") }) { Text(note) }
    }
}

@Composable
internal fun SqlObjectSourceCheckbox(
    sourceName: String,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Label(attrs = {
        classes("sql-object-source-checkbox")
        if (selected) {
            classes("sql-object-source-checkbox-selected")
        }
    }) {
        Input(type = org.jetbrains.compose.web.attributes.InputType.Checkbox, attrs = {
            if (selected) {
                attr("checked", "checked")
            }
            onClick { onToggle() }
        })
        Span { Text(sourceName) }
    }
}

@Composable
internal fun SqlObjectSearchButton(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", "btn-dark")
        attr("type", "button")
        if (loading || !enabled) {
            disabled()
        }
        onClick { onClick() }
    }) {
        Text(if (loading) "Поиск..." else "Искать")
    }
}
