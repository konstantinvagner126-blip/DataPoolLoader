package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.SectionCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.dom.classesFromString
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File

@Composable
internal fun CredentialsPanel(
    module: ModuleDetailsResponse,
    sectionStateKey: String?,
    uploadInProgress: Boolean,
    selectedFileName: String?,
    uploadMessage: String?,
    uploadMessageLevel: String,
    onFileSelected: (File?) -> Unit,
    onUpload: () -> Unit,
) {
    val status = module.credentialsStatus
    var expanded by remember(sectionStateKey) {
        mutableStateOf(loadSectionExpanded(sectionStateKey, defaultExpanded = true))
    }
    val warningClass = when {
        !module.requiresCredentials -> "alert alert-light mb-0"
        module.credentialsReady -> "alert alert-success mb-0"
        else -> "alert alert-warning mb-0"
    }

    SectionCard(
        title = "credential.properties",
        subtitle = buildCredentialsStatusText(status),
        actions = {
            SectionExpandToggleButton(expanded) {
                val nextValue = !expanded
                expanded = nextValue
                saveSectionExpanded(sectionStateKey, nextValue)
            }
        },
    ) {
        if (expanded) {
            Div({ classes("d-flex", "flex-wrap", "align-items-center", "justify-content-between", "gap-3") }) {
                Div({ classes("d-flex", "flex-wrap", "align-items-center", "gap-2") }) {
                    Input(type = org.jetbrains.compose.web.attributes.InputType.File, attrs = {
                        classes("form-control")
                        attr("accept", ".properties,text/plain")
                        onChange { event ->
                            val input = event.target as? HTMLInputElement
                            onFileSelected(input?.files?.item(0))
                        }
                    })
                    Button(attrs = {
                        classes("btn", "btn-outline-dark")
                        attr("type", "button")
                        if (uploadInProgress || selectedFileName.isNullOrBlank()) {
                            disabled()
                        }
                        onClick { onUpload() }
                    }) {
                        Text(if (uploadInProgress) "Загрузка..." else "Загрузить файл")
                    }
                }
            }

            if (!selectedFileName.isNullOrBlank()) {
                EditorRunMutedText("Выбран файл: $selectedFileName", "mt-2")
            }

            if (!uploadMessage.isNullOrBlank()) {
                AlertBanner(uploadMessage, uploadMessageLevel)
            }

            Div({ classesFromString(warningClass) }) {
                Text(buildCredentialsWarningText(module))
            }
        }
    }
}
