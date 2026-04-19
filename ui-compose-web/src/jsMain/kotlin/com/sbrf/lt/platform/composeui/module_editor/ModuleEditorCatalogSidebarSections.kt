package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.model.ModuleCatalogItem
import com.sbrf.lt.platform.composeui.module_editor.buildCatalogStatus
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun ModuleCatalogSidebar(
    route: ModuleEditorRouteState,
    state: ModuleEditorPageState,
    capabilities: ModuleLifecycleCapabilities?,
    actionBusy: Boolean,
    onOpenCreateModule: () -> Unit,
    onDeleteModule: () -> Unit,
    onImportModules: () -> Unit,
    onToggleIncludeHidden: () -> Unit,
    onSelectModule: (String) -> Unit,
) {
    Div({ classes("panel", "h-100") }) {
        Div({ classes("panel-title") }) {
            Text(if (route.storage == "database") "Модули из базы данных" else "Модули")
        }
        Div({
            classes("module-catalog-status", "mt-3", "mb-3", "text-secondary", "small")
        }) {
            Text(buildCatalogStatus(state, route.storage))
        }
        if (route.storage == "database") {
            ModuleCatalogActionBar(
                capabilities = capabilities,
                actionBusy = actionBusy,
                selectedModuleId = state.selectedModuleId,
                onOpenCreateModule = onOpenCreateModule,
                onDeleteModule = onDeleteModule,
                onImportModules = onImportModules,
            )
            Label(attrs = { classes("config-form-check", "mb-3") }) {
                Input(type = org.jetbrains.compose.web.attributes.InputType.Checkbox, attrs = {
                    classes("form-check-input")
                    if (route.includeHidden) {
                        attr("checked", "checked")
                    }
                    onClick { onToggleIncludeHidden() }
                })
                Span({ classes("form-check-label") }) { Text("Показывать скрытые модули") }
            }
        }
        if (route.storage == "database" && route.includeHidden) {
            Div({ classes("text-secondary", "small", "mb-3") }) {
                Text("Каталог открыт с показом скрытых модулей.")
            }
        }
        ModuleCatalogBody(
            loading = state.loading,
            modules = state.modules,
            selectedModuleId = state.selectedModuleId,
            onSelectModule = onSelectModule,
        )
    }
}

@Composable
internal fun ModuleCatalogActionBar(
    capabilities: ModuleLifecycleCapabilities?,
    actionBusy: Boolean,
    selectedModuleId: String?,
    onOpenCreateModule: () -> Unit,
    onDeleteModule: () -> Unit,
    onImportModules: () -> Unit,
) {
    Div({ classes("module-catalog-actions", "mb-3") }) {
        EditorIconActionButton(
            icon = "+",
            label = "Новый",
            title = "Новый модуль",
            enabled = capabilities?.createModule == true && !actionBusy,
            style = EditorActionStyle.PrimaryOutline,
            onClick = onOpenCreateModule,
        )
        EditorIconActionButton(
            icon = "−",
            label = "Удалить",
            title = "Удалить модуль",
            enabled = capabilities?.deleteModule == true && !actionBusy && selectedModuleId != null,
            style = EditorActionStyle.DangerOutline,
            onClick = onDeleteModule,
        )
        EditorIconActionButton(
            icon = "⇅",
            label = "Импорт",
            title = "Импорт из файлов",
            enabled = true,
            style = EditorActionStyle.SecondaryOutline,
            onClick = onImportModules,
        )
    }
}

@Composable
internal fun ModuleCatalogBody(
    loading: Boolean,
    modules: List<ModuleCatalogItem>,
    selectedModuleId: String?,
    onSelectModule: (String) -> Unit,
) {
    if (loading && modules.isEmpty()) {
        P({ classes("text-secondary", "small", "mb-0") }) {
            Text("Каталог модулей загружается.")
        }
    } else {
        Div({ classes("list-group", "module-list") }) {
            modules.forEach { module ->
                ModuleCatalogListItem(
                    module = module,
                    selected = module.id == selectedModuleId,
                    onClick = { onSelectModule(module.id) },
                )
            }
        }
    }
}
