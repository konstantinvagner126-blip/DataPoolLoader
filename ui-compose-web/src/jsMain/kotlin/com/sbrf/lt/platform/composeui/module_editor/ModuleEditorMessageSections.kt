package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.runtime.buildDatabaseModeUnavailableMessage
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun ModuleRunningBadge() {
    Span({ classes("module-running-badge") }) {
        Span({
            classes("module-running-badge-spinner")
            attr("aria-hidden", "true")
        })
        Span({
            classes("module-running-badge-arrows")
            attr("aria-hidden", "true")
        }) {
            Span({ classes("module-running-badge-arrow", "module-running-badge-arrow-forward") }) {
                Text("↻")
            }
            Span({ classes("module-running-badge-arrow", "module-running-badge-arrow-backward") }) {
                Text("↺")
            }
        }
        Text("Выполняется")
    }
}

@Composable
internal fun EditorErrorMessageBox(
    message: String,
    onDismiss: () -> Unit,
) {
    Div({ classes("editor-message-box") }) {
        Div({ classes("editor-message-box-head") }) {
            Div({ classes("editor-message-box-title") }) { Text("Операция не выполнена") }
            Button(attrs = {
                classes("btn", "btn-outline-secondary", "btn-sm")
                attr("type", "button")
                onClick { onDismiss() }
            }) {
                Text("Закрыть")
            }
        }
        Div({ classes("editor-message-box-text") }) {
            Text(message)
        }
    }
}

@Composable
internal fun DatabaseModeAlert(
    route: ModuleEditorRouteState,
    state: ModuleEditorPageState,
) {
    if (route.storage != "database") {
        return
    }
    val runtimeContext = state.databaseCatalog?.runtimeContext ?: return
    if (runtimeContext.effectiveMode == ModuleStoreMode.DATABASE) {
        return
    }

    Div({ classes("alert", "alert-warning", "mb-4") }) {
        Div({ classes("fw-semibold", "mb-1") }) {
            Text("Режим базы данных недоступен")
        }
        Text(
            buildDatabaseModeUnavailableMessage(
                runtimeContext.fallbackReason,
                "Для работы с модулями из базы данных нужно переключить режим на «База данных» и убедиться, что PostgreSQL доступен.",
            ),
        )
    }
}
