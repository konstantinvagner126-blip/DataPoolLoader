package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun ExternalSqlAlert(
    externalRef: String?,
    storageMode: String,
) {
    if (externalRef.isNullOrBlank()) {
        return
    }
    Div({ classes("alert", if (storageMode == "database") "alert-warning" else "alert-secondary", "mb-0", "w-100") }) {
        Text(buildExternalSqlAlertText(externalRef, storageMode))
    }
}

@Composable
internal fun CommitSqlModeFields(
    mode: String,
    inlineText: String,
    catalogPath: String,
    externalRef: String?,
    sqlResources: List<SqlResourceOption>,
    disabled: Boolean,
    storageMode: String,
    inlineRowsCount: Int,
    inlineHelpText: String,
    onInlineCommit: (String) -> Unit,
    onCatalogCommit: (String) -> Unit,
) {
    when (mode) {
        "INLINE" -> CommitTextareaField(
            label = "Встроенный SQL",
            value = inlineText,
            disabled = disabled,
            rowsCount = inlineRowsCount,
            helpText = inlineHelpText,
            onCommit = onInlineCommit,
        )

        "CATALOG" -> CommitSelectField(
            label = "SQL-ресурс",
            value = catalogPath,
            options = sqlResources.map { it.path to catalogLabel(it) },
            disabled = disabled,
            helpText = "Выбирается из вкладки SQL.",
            onCommit = onCatalogCommit,
        )

        "EXTERNAL" -> ExternalSqlAlert(externalRef, storageMode)
    }
}
