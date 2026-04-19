package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label

@Composable
internal fun CommitIntField(
    label: String,
    value: Int,
    disabled: Boolean,
    helpText: String = "",
    onCommit: (Int) -> Unit,
) = CommitRequiredNumericField(
    label = label,
    valueText = value.toString(),
    disabled = disabled,
    helpText = helpText,
    parse = String::toIntOrNull,
    onCommit = onCommit,
)

@Composable
internal fun CommitOptionalIntField(
    label: String,
    value: Int?,
    disabled: Boolean,
    helpText: String = "",
    onCommit: (Int?) -> Unit,
) = CommitOptionalNumericField(
    label = label,
    valueText = value?.toString().orEmpty(),
    disabled = disabled,
    helpText = helpText,
    parse = String::toIntOrNull,
    onCommit = onCommit,
)

@Composable
internal fun CommitLongField(
    label: String,
    value: Long,
    disabled: Boolean,
    helpText: String = "",
    onCommit: (Long) -> Unit,
) = CommitRequiredNumericField(
    label = label,
    valueText = value.toString(),
    disabled = disabled,
    helpText = helpText,
    parse = String::toLongOrNull,
    onCommit = onCommit,
)

@Composable
internal fun CommitOptionalLongField(
    label: String,
    value: Long?,
    disabled: Boolean,
    helpText: String = "",
    onCommit: (Long?) -> Unit,
) = CommitOptionalNumericField(
    label = label,
    valueText = value?.toString().orEmpty(),
    disabled = disabled,
    helpText = helpText,
    parse = String::toLongOrNull,
    onCommit = onCommit,
)

@Composable
internal fun CommitOptionalDoubleField(
    label: String,
    value: Double?,
    disabled: Boolean,
    helpText: String = "",
    onCommit: (Double?) -> Unit,
) = CommitOptionalNumericField(
    label = label,
    valueText = value?.toString().orEmpty(),
    disabled = disabled,
    helpText = helpText,
    parse = String::toDoubleOrNull,
    onCommit = onCommit,
)

@Composable
internal fun CommitNumericTextField(
    label: String,
    valueText: String,
    disabled: Boolean,
    helpText: String,
    onCommitText: (String) -> Unit,
) {
    var draft by remember(valueText) { mutableStateOf(valueText) }
    Label(attrs = { classes("config-form-field") }) {
        ConfigFieldHeader(label, helpText)
        Input(type = InputType.Text, attrs = {
            classes("form-control")
            value(draft)
            if (disabled) {
                disabled()
            }
            onInput { draft = it.value }
            onChange {
                onCommitText(draft)
                draft = draft.ifBlank { valueText }
            }
        })
    }
}

@Composable
private fun <T> CommitRequiredNumericField(
    label: String,
    valueText: String,
    disabled: Boolean,
    helpText: String,
    parse: (String) -> T?,
    onCommit: (T) -> Unit,
) = CommitNumericTextField(
    label = label,
    valueText = valueText,
    disabled = disabled,
    helpText = helpText,
    onCommitText = { nextValue ->
        parse(nextValue)?.let(onCommit)
    },
)

@Composable
private fun <T> CommitOptionalNumericField(
    label: String,
    valueText: String,
    disabled: Boolean,
    helpText: String,
    parse: (String) -> T?,
    onCommit: (T?) -> Unit,
) = CommitNumericTextField(
    label = label,
    valueText = valueText,
    disabled = disabled,
    helpText = helpText,
    onCommitText = { nextValue ->
        if (nextValue.isBlank()) {
            onCommit(null)
        } else {
            parse(nextValue)?.let(onCommit)
        }
    },
)
