package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

@Composable
internal fun ValidationAlert(session: ModuleEditorSessionResponse) {
    val issues = session.module.validationIssues
    if (issues.isEmpty() && session.module.validationStatus.equals("VALID", ignoreCase = true)) {
        return
    }
    val alertClass = when (session.module.validationStatus.uppercase()) {
        "INVALID" -> "alert alert-danger"
        else -> "alert alert-warning"
    }
    Div({
        classes("mb-3")
        classes(alertClass)
    }) {
        Div({ classes("fw-semibold", "mb-2") }) {
            Text("Проблемы валидации модуля")
        }
        Ul({ classes("module-validation-list", "mb-0") }) {
            issues.forEach { issue ->
                Li {
                    ValidationSeverityBadge(issue.severity)
                    Text(issue.message)
                }
            }
        }
    }
}

@Composable
internal fun ValidationSeverityBadge(
    severity: String,
) {
    val isError = severity.equals("ERROR", ignoreCase = true)
    Span({
        classes(
            "module-validation-severity",
            if (isError) "module-validation-severity-error" else "module-validation-severity-warning",
        )
    }) {
        Text(if (isError) "Ошибка" else "Предупреждение")
    }
}
