package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.model.ModuleCatalogItem
import com.sbrf.lt.platform.composeui.module_editor.translateValidationStatus
import com.sbrf.lt.platform.composeui.module_editor.validationBadgeClass
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun ModuleCatalogListItem(
    module: ModuleCatalogItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val moduleDescription = module.description
    Button(attrs = {
        classes("list-group-item", "list-group-item-action")
        if (selected) {
            classes("active")
        }
        attr("type", "button")
        onClick { onClick() }
    }) {
        Div({ classes("module-list-head") }) {
            Div {
                Div({ classes("module-list-title") }) {
                    Text(module.title.ifBlank { module.id })
                }
                if (!moduleDescription.isNullOrBlank()) {
                    Div({ classes("module-list-description") }) {
                        Text(moduleDescription)
                    }
                }
            }
            Span({
                classes("module-validation-badge", validationBadgeClass(module.validationStatus))
            }) {
                Text(translateValidationStatus(module.validationStatus))
            }
            if (module.hasActiveRun) {
                ModuleRunningBadge()
            }
            if (module.hiddenFromUi) {
                Span({ classes("module-validation-badge", "module-validation-badge-warning") }) {
                    Text("Скрыт")
                }
            }
        }
        if (module.tags.isNotEmpty()) {
            Div({ classes("module-list-tags") }) {
                module.tags.forEach { tag ->
                    Span({ classes("module-tag") }) { Text(tag) }
                }
            }
        }
    }
}
