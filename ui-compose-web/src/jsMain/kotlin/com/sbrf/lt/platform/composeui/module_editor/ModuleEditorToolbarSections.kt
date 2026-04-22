package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun EditorShellHeader(
    route: ModuleEditorRouteState,
    state: ModuleEditorPageState,
    onTabSelect: (ModuleEditorTab) -> Unit,
    onRun: () -> Unit,
    onSave: () -> Unit,
    onDiscardWorkingCopy: () -> Unit,
    onPublishWorkingCopy: () -> Unit,
    onOpenCreateModule: () -> Unit,
    onDeleteModule: () -> Unit,
    onReload: () -> Unit,
) {
    val session = requireNotNull(state.session)
    val module = session.module
    val capabilities = session.capabilities
    val actionBusy = state.actionInProgress != null
    val moduleDescription = module.description

    Div({ classes("panel", "mb-4") }) {
        Div({ classes("module-editor-header-shell") }) {
            Div {
                Div({ classes("panel-title", "mb-1") }) { Text("Редактор модуля") }
                Div({ classes("text-secondary", "small") }) { Text(module.title.ifBlank { module.id }) }
                if (!moduleDescription.isNullOrBlank()) {
                    Div({ classes("text-secondary", "small", "mt-1") }) { Text(moduleDescription) }
                }
                Div({ classes("module-draft-status", "small", "mt-1", "text-secondary") }) {
                    Span({
                        classes(
                            "module-draft-dot",
                            when {
                                state.hasDraftChanges -> "module-draft-dot-dirty"
                                route.storage == "database" && !session.workingCopyId.isNullOrBlank() -> "module-draft-dot-neutral"
                                else -> "module-draft-dot-saved"
                            },
                        )
                        attr("aria-hidden", "true")
                    })
                    Span {
                        Text(buildDraftStatusText(route, session, state.hasDraftChanges))
                    }
                }
                if (route.storage == "database" && !session.sourceKind.isNullOrBlank()) {
                    Div({ classes("small", "mt-1", "text-secondary") }) {
                        Text("Источник данных редактора: ${translateSourceKind(session.sourceKind)}")
                    }
                }
            }

            if (route.storage == "database") {
                EditorToolbar {
                    EditorToolbarGroup("Выполнение", "module-editor-toolbar-group-primary") {
                        EditorActionButton("Запустить", capabilities.run && !actionBusy, EditorActionStyle.Success, onRun)
                        RunsHistoryLinkButton(route, state.selectedModuleId)
                    }
                    EditorToolbarGroup("Личный черновик", "module-editor-toolbar-group-draft") {
                        EditorActionButton(
                            "Сохранить черновик",
                            capabilities.saveWorkingCopy && state.hasDraftChanges && !actionBusy,
                            EditorActionStyle.PrimarySolid,
                            onSave,
                        )
                        EditorActionButton("Опубликовать", capabilities.publish && !actionBusy && !state.hasDraftChanges, EditorActionStyle.Success, onPublishWorkingCopy)
                        EditorActionButton("Сбросить черновик", capabilities.discardWorkingCopy && !actionBusy, EditorActionStyle.DangerOutline, onDiscardWorkingCopy)
                    }
                    EditorToolbarGroup("Редактор", "module-editor-toolbar-group-secondary") {
                        EditorActionButton("Отменить изменения", state.hasDraftChanges && !actionBusy, EditorActionStyle.DangerOutline, onReload)
                    }
                }
            } else {
                EditorToolbar {
                    EditorToolbarGroup("Выполнение", "module-editor-toolbar-group-primary") {
                        RunsHistoryLinkButton(route, state.selectedModuleId)
                        EditorActionButton("Запустить", capabilities.run && !actionBusy, EditorActionStyle.PrimarySolid, onRun)
                    }
                    EditorToolbarGroup("Редактор", "module-editor-toolbar-group-secondary") {
                        EditorActionButton("Отменить изменения", state.hasDraftChanges && !actionBusy, EditorActionStyle.DangerOutline, onReload)
                        EditorActionButton("Сохранить", capabilities.save && state.hasDraftChanges && !actionBusy, EditorActionStyle.PrimarySolid, onSave)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RunsHistoryLinkButton(
    route: ModuleEditorRouteState,
    moduleId: String?,
) {
    A(attrs = {
        classes("btn", "btn-outline-secondary")
        if (moduleId == null) {
            classes("disabled")
        }
        if (moduleId != null) {
            href(buildRunsHref(route, moduleId))
        } else {
            attr("aria-disabled", "true")
        }
    }) { Text("История и результаты") }
}

@Composable
internal fun EditorToolbarGroup(
    label: String,
    vararg groupClasses: String,
    content: @Composable () -> Unit,
) {
    Div({ classes("module-editor-toolbar-group", *groupClasses) }) {
        Div({ classes("module-editor-toolbar-group-label") }) { Text(label) }
        content()
    }
}

@Composable
internal fun EditorToolbar(
    content: @Composable () -> Unit,
) {
    Div({ classes("module-editor-toolbar") }) {
        Div({ classes("module-editor-toolbar-row") }) {
            content()
        }
    }
}
