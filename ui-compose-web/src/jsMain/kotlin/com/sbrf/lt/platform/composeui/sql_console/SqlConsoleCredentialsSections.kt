package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.model.CredentialsStatusResponse
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File

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
                AlertBanner(credentialsMessage, credentialsMessageLevel)
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
