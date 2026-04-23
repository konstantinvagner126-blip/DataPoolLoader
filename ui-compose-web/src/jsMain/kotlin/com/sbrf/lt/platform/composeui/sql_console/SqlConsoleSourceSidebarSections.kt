package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Text
import org.w3c.files.File

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
    onToggleSourceGroup: (SqlConsoleSourceGroup, Boolean) -> Unit,
    onToggleSource: (String, Boolean) -> Unit,
    onCredentialsFileSelected: (File?) -> Unit,
    onUploadCredentials: () -> Unit,
) {
    Div({ classes("panel", "sql-shell-pane", "sql-sidebar-panel", "h-100") }) {
        Div({ classes("sql-shell-pane-head", "sql-sidebar-pane-head") }) {
            Div {
                Div({ classes("eyebrow", "mb-1") }) { Text("Navigator") }
                Div({ classes("panel-title", "mb-0") }) { Text("Sources") }
            }
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
        Div({ classes("small", "text-secondary", "sql-shell-pane-note", "mb-3") }) {
            Text(buildConsoleInfoText(state.info))
        }
        SqlConsoleSourceSettingsBlock(
            state = state,
            onMaxRowsDraftChange = onMaxRowsDraftChange,
            onTimeoutDraftChange = onTimeoutDraftChange,
            onSaveSettings = onSaveSettings,
        )
        state.connectionCheck?.let { connectionCheck ->
            Div({ classes("small", "text-secondary", "mt-2") }) {
                Text(buildConnectionCheckStatusText(connectionCheck))
            }
        } ?: Div({ classes("small", "text-secondary", "mt-2") }) {
            Text("Явная проверка подключений еще не выполнялась. Индикаторы ниже обновляются также по факту выполнения SQL.")
        }
        Div({ classes("small", "text-secondary", "mt-3") }) {
            Text("Выбери, по каким источникам выполнять запрос.")
        }
        SqlConsoleSourceSelectionBlock(
            groups = state.info?.groups.orEmpty(),
            sourceCatalogNames = state.info?.sourceCatalog.orEmpty().map { it.name },
            selectedSourceNames = state.selectedSourceNames,
            connectionStatusBySource = connectionStatusBySource,
            onToggleSourceGroup = onToggleSourceGroup,
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
private fun SqlConsoleSourceSettingsBlock(
    state: SqlConsolePageState,
    onMaxRowsDraftChange: (String) -> Unit,
    onTimeoutDraftChange: (String) -> Unit,
    onSaveSettings: () -> Unit,
) {
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
}
