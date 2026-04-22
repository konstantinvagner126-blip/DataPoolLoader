package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun ConfigCollectionCard(
    title: String,
    note: String? = null,
    removeLabel: String,
    disabled: Boolean,
    onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    Div({ classes("config-form-card", "mb-3") }) {
        Div({ classes("config-form-card-body") }) {
            Div({ classes("d-flex", "justify-content-between", "align-items-center", "mb-3", "gap-2") }) {
                Div {
                    Div({ classes("config-form-card-title") }) {
                        Text(title)
                    }
                    if (!note.isNullOrBlank()) {
                        Div({ classes("config-form-help") }) {
                            Text(note)
                        }
                    }
                }
                ConfigCollectionActionButton(
                    label = removeLabel,
                    toneClass = "btn-outline-danger",
                    disabled = disabled,
                ) {
                    onRemove()
                }
            }
            Div({ classes("config-form-fields") }) {
                content()
            }
        }
    }
}

@Composable
internal fun ConfigCollectionActionButton(
    label: String,
    toneClass: String,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", toneClass, "btn-sm")
        attr("type", "button")
        if (disabled) {
            disabled()
        }
        onClick { onClick() }
    }) {
        Text(label)
    }
}
