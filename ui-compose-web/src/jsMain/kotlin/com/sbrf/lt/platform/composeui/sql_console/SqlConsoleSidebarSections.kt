package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File

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

@Composable
internal fun SqlConsoleSourceSidebar(
    state: SqlConsolePageState,
    connectionStatusBySource: Map<String, SqlConsoleSourceConnectionStatus>,
    credentialsStatus: CredentialsStatusResponse?,
    credentialsMessage: String?,
    credentialsMessageLevel: String,
    selectedCredentialsFile: File?,
    credentialsUploadInProgress: Boolean,
    onCheckConnections: () -> Unit,
    onMaxRowsDraftChange: (String) -> Unit,
    onTimeoutDraftChange: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onToggleSource: (String, Boolean) -> Unit,
    onCredentialsFileSelected: (File?) -> Unit,
    onUploadCredentials: () -> Unit,
) {
    Div({ classes("panel", "sql-sidebar-panel", "h-100") }) {
        Div({ classes("d-flex", "align-items-center", "justify-content-between", "gap-2", "mb-2") }) {
            Div({ classes("panel-title", "mb-0") }) { Text("Sources") }
            Button(attrs = {
                classes("btn", "btn-outline-dark", "btn-sm")
                attr("type", "button")
                if (state.actionInProgress == "check-connections") {
                    disabled()
                }
                onClick { onCheckConnections() }
            }) {
                Text("Проверить подключение")
            }
        }
        Div({ classes("small", "text-secondary", "mb-3") }) {
            Text(buildConsoleInfoText(state.info))
        }
        Div({ classes("sql-source-settings", "mt-3") }) {
            Label(attrs = {
                classes("small", "text-secondary", "mb-1")
                attr("for", "composeSqlMaxRows")
            }) { Text("Лимит строк на source") }
            Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                Input(type = InputType.Number, attrs = {
                    id("composeSqlMaxRows")
                    classes("form-control", "form-control-sm", "sql-source-limit-input")
                    value(state.maxRowsPerShardDraft)
                    onInput { onMaxRowsDraftChange(it.value?.toString().orEmpty()) }
                })
            }
            Label(attrs = {
                classes("small", "text-secondary", "mt-3", "mb-1")
                attr("for", "composeSqlTimeout")
            }) { Text("Таймаут запроса, сек") }
            Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                Input(type = InputType.Number, attrs = {
                    id("composeSqlTimeout")
                    classes("form-control", "form-control-sm", "sql-source-limit-input")
                    value(state.queryTimeoutSecDraft)
                    onInput { onTimeoutDraftChange(it.value?.toString().orEmpty()) }
                })
                Button(attrs = {
                    classes("btn", "btn-outline-secondary", "btn-sm")
                    attr("type", "button")
                    if (state.actionInProgress == "save-settings") {
                        disabled()
                    }
                    onClick { onSaveSettings() }
                }) {
                    Text("Сохранить")
                }
            }
        }
        state.connectionCheck?.let { connectionCheck ->
            Div({ classes("small", "text-secondary", "mt-2") }) {
                Text(buildConnectionCheckStatusText(connectionCheck))
            }
        } ?: Div({ classes("small", "text-secondary", "mt-2") }) {
            Text("Проверка подключений еще не выполнялась.")
        }
        Div({ classes("small", "text-secondary", "mt-3") }) {
            Text("Выбери, по каким источникам выполнять запрос.")
        }
        SqlConsoleSourceSelectionBlock(
            sourceNames = state.info?.sourceNames.orEmpty(),
            selectedSourceNames = state.selectedSourceNames,
            connectionStatusBySource = connectionStatusBySource,
            onToggleSource = onToggleSource,
        )
        SqlConsoleCredentialsPanel(
            credentialsStatus = credentialsStatus,
            credentialsMessage = credentialsMessage,
            credentialsMessageLevel = credentialsMessageLevel,
            selectedCredentialsFile = selectedCredentialsFile,
            credentialsUploadInProgress = credentialsUploadInProgress,
            onFileSelected = onCredentialsFileSelected,
            onUpload = onUploadCredentials,
        )
    }
}

@Composable
internal fun SqlConsoleSourceSelectionBlock(
    sourceNames: List<String>,
    selectedSourceNames: List<String>,
    connectionStatusBySource: Map<String, SqlConsoleSourceConnectionStatus>,
    onToggleSource: (String, Boolean) -> Unit,
) {
    Div({ classes("mt-3", "sql-source-selection") }) {
        sourceNames.forEach { sourceName ->
            val sourceStatus = connectionStatusBySource[sourceName]
            val selected = sourceName in selectedSourceNames
            SqlConsoleSourceCheckbox(
                sourceName = sourceName,
                sourceStatus = sourceStatus,
                selected = selected,
                onToggle = { onToggleSource(sourceName, !selected) },
            )
        }
    }
}

@Composable
internal fun SqlConsoleSourceCheckbox(
    sourceName: String,
    sourceStatus: SqlConsoleSourceConnectionStatus?,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Label(attrs = {
        classes("sql-source-checkbox", "sql-source-checkbox-${sourceStatusTone(sourceStatus)}")
        if (selected) {
            classes("sql-source-checkbox-selected")
        }
    }) {
        Input(type = InputType.Checkbox, attrs = {
            if (selected) {
                attr("checked", "checked")
            }
            onClick { onToggle() }
        })
        Div({ classes("sql-source-checkbox-body") }) {
            Div({ classes("sql-source-checkbox-head") }) {
                Span({ classes("sql-source-checkbox-name") }) {
                    Text(sourceName)
                }
                Span({ classes("sql-source-checkbox-status") }) {
                    Text(sourceStatus?.let { translateSourceStatus(it.status) } ?: "Не проверено")
                }
            }
            Div({ classes("sql-source-checkbox-message") }) {
                Text(
                    sourceStatus?.errorMessage
                        ?: sourceStatus?.message
                        ?: "Подключение еще не проверялось. Выбери source и запусти проверку.",
                )
            }
        }
    }
}

@Composable
internal fun SqlConsoleCredentialsPanel(
    credentialsStatus: CredentialsStatusResponse?,
    credentialsMessage: String?,
    credentialsMessageLevel: String,
    selectedCredentialsFile: File?,
    credentialsUploadInProgress: Boolean,
    onFileSelected: (File?) -> Unit,
    onUpload: () -> Unit,
) {
    Div({ classes("sql-credentials-details", "mt-4") }) {
        Div({ classes("sql-credentials-summary") }) {
            Text("credential.properties")
        }
        Div({ classes("mt-3") }) {
            Div({ classes("small", "text-secondary", "mb-2") }) {
                Text(credentialsStatus?.let(::buildCredentialsStatusText) ?: "Файл не загружен.")
            }
            if (!credentialsMessage.isNullOrBlank()) {
                AlertBanner(credentialsMessage ?: "", credentialsMessageLevel)
            }
            Div({ classes("d-flex", "flex-wrap", "align-items-center", "gap-2", "mt-3") }) {
                Input(type = InputType.File, attrs = {
                    classes("form-control")
                    attr("accept", ".properties,text/plain")
                    onChange {
                        val input = it.target as? HTMLInputElement
                        onFileSelected(input?.files?.item(0))
                    }
                })
                Button(attrs = {
                    classes("btn", "btn-outline-dark")
                    attr("type", "button")
                    if (selectedCredentialsFile == null || credentialsUploadInProgress) {
                        disabled()
                    }
                    onClick { onUpload() }
                }) {
                    Text("Загрузить файл")
                }
            }
        }
    }
}
