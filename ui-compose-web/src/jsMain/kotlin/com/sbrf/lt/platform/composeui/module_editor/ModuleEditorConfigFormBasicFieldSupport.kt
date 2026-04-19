package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.rows
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

@Composable
internal fun ConfigSectionCard(
    title: String,
    subtitle: String? = null,
    sectionStateKey: String? = null,
    defaultExpanded: Boolean = true,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var expanded by remember(sectionStateKey) {
        mutableStateOf(loadSectionExpanded(sectionStateKey, defaultExpanded))
    }
    SectionCard(
        title = title,
        subtitle = subtitle,
        actions = {
            if (actions != null) {
                actions()
            }
            if (sectionStateKey != null) {
                SectionExpandToggleButton(expanded) {
                    val nextValue = !expanded
                    expanded = nextValue
                    saveSectionExpanded(sectionStateKey, nextValue)
                }
            }
        },
    ) {
        if (expanded) {
            content()
        }
    }
}

@Composable
internal fun CommitTextField(
    label: String,
    value: String,
    disabled: Boolean,
    helpText: String = "",
    wide: Boolean = false,
    onCommit: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    Label(attrs = {
        classes("config-form-field", *if (wide) arrayOf("config-form-field-wide") else emptyArray())
    }) {
        ConfigFieldHeader(label, helpText)
        Input(type = InputType.Text, attrs = {
            classes("form-control")
            value(draft)
            if (disabled) {
                disabled()
            }
            onInput { draft = it.value }
            onChange { if (draft != value) onCommit(draft) }
        })
    }
}

@Composable
internal fun CommitTextareaField(
    label: String,
    value: String,
    disabled: Boolean,
    rowsCount: Int,
    helpText: String = "",
    onCommit: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    Label(attrs = { classes("config-form-field", "config-form-field-wide") }) {
        ConfigFieldHeader(label, helpText)
        TextArea(value = draft, attrs = {
            classes("form-control")
            rows(rowsCount)
            if (disabled) {
                disabled()
            }
            onInput { draft = it.value }
            onChange { if (draft != value) onCommit(draft) }
        })
    }
}

@Composable
internal fun CommitSelectField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    disabled: Boolean,
    helpText: String = "",
    onCommit: (String) -> Unit,
) {
    Label(attrs = { classes("config-form-field") }) {
        ConfigFieldHeader(label, helpText)
        Select(attrs = {
            classes("form-select")
            attr("value", value)
            if (disabled) {
                disabled()
            }
            onChange { event -> onCommit(event.value ?: "") }
        }) {
            options.forEach { (optionValue, optionLabel) ->
                Option(value = optionValue) {
                    Text(optionLabel)
                }
            }
        }
    }
}

@Composable
internal fun CommitCheckboxField(
    label: String,
    checked: Boolean,
    disabled: Boolean,
    helpText: String = "",
    onCommit: (Boolean) -> Unit,
) {
    Div({ classes("config-form-check-group") }) {
        Label(attrs = { classes("config-form-check") }) {
            Input(type = InputType.Checkbox, attrs = {
                classes("form-check-input")
                if (checked) {
                    attr("checked", "checked")
                }
                if (disabled) {
                    disabled()
                }
                onClick { onCommit(!checked) }
            })
            Span({ classes("form-check-label") }) { Text(label) }
        }
        if (helpText.isNotBlank()) {
            Div({ classes("config-form-help") }) { Text(helpText) }
        }
    }
}

@Composable
internal fun ConfigFieldHeader(
    label: String,
    helpText: String,
) {
    Span({ classes("config-form-label") }) { Text(label) }
    if (helpText.isNotBlank()) {
        Span({ classes("config-form-help") }) { Text(helpText) }
    }
}
