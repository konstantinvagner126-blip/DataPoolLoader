package com.sbrf.lt.platform.composeui.module_editor

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.runtime.buildDatabaseModeUnavailableMessage
import com.sbrf.lt.platform.composeui.model.ModuleStoreMode
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.dom.A
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
                Div({ classes("module-editor-toolbar") }) {
                    Div({ classes("module-editor-toolbar-row") }) {
                        Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-primary") }) {
                            Div({ classes("module-editor-toolbar-group-label") }) { Text("Выполнение") }
                            EditorActionButton("Запустить", capabilities.run && !actionBusy, EditorActionStyle.Success, onRun)
                            A(attrs = {
                                classes("btn", "btn-outline-secondary")
                                if (state.selectedModuleId == null) {
                                    classes("disabled")
                                }
                                if (state.selectedModuleId != null) {
                                    href(buildRunsHref(route, state.selectedModuleId))
                                } else {
                                    attr("aria-disabled", "true")
                                }
                            }) { Text("История и результаты") }
                        }
                        Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-draft") }) {
                            Div({ classes("module-editor-toolbar-group-label") }) { Text("Личный черновик") }
                            EditorActionButton(
                                "Сохранить черновик",
                                capabilities.saveWorkingCopy && state.hasDraftChanges && !actionBusy,
                                EditorActionStyle.PrimarySolid,
                                onSave,
                            )
                            EditorActionButton("Опубликовать", capabilities.publish && !actionBusy && !state.hasDraftChanges, EditorActionStyle.Success, onPublishWorkingCopy)
                            EditorActionButton("Сбросить черновик", capabilities.discardWorkingCopy && !actionBusy, EditorActionStyle.DangerOutline, onDiscardWorkingCopy)
                        }
                        Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-secondary") }) {
                            Div({ classes("module-editor-toolbar-group-label") }) { Text("Редактор") }
                            EditorActionButton("Отменить изменения", state.hasDraftChanges && !actionBusy, EditorActionStyle.DangerOutline, onReload)
                        }
                    }
                }
            } else {
                Div({ classes("module-editor-toolbar") }) {
                    Div({ classes("module-editor-toolbar-row") }) {
                        Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-primary") }) {
                            Div({ classes("module-editor-toolbar-group-label") }) { Text("Выполнение") }
                            A(attrs = {
                                classes("btn", "btn-outline-secondary")
                                if (state.selectedModuleId == null) {
                                    classes("disabled")
                                }
                                if (state.selectedModuleId != null) {
                                    href(buildRunsHref(route, state.selectedModuleId))
                                } else {
                                    attr("aria-disabled", "true")
                                }
                            }) { Text("История и результаты") }
                            EditorActionButton("Запустить", capabilities.run && !actionBusy, EditorActionStyle.PrimarySolid, onRun)
                        }
                        Div({ classes("module-editor-toolbar-group", "module-editor-toolbar-group-secondary") }) {
                            Div({ classes("module-editor-toolbar-group-label") }) { Text("Редактор") }
                            EditorActionButton("Отменить изменения", state.hasDraftChanges && !actionBusy, EditorActionStyle.DangerOutline, onReload)
                            EditorActionButton("Сохранить", capabilities.save && state.hasDraftChanges && !actionBusy, EditorActionStyle.PrimarySolid, onSave)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditorActionButton(
    label: String,
    enabled: Boolean,
    style: EditorActionStyle = EditorActionStyle.SecondaryOutline,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", style.cssClass)
        if (!enabled) {
            disabled()
        }
        attr("type", "button")
        if (enabled) {
            onClick { onClick() }
        }
    }) {
        Text(label)
    }
}

@Composable
internal fun EditorIconActionButton(
    icon: String,
    label: String,
    title: String,
    enabled: Boolean,
    style: EditorActionStyle = EditorActionStyle.SecondaryOutline,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", "module-editor-icon-btn", style.cssClass)
        attr("type", "button")
        attr("title", title)
        attr("aria-label", title)
        if (!enabled) {
            disabled()
        }
        if (enabled) {
            onClick { onClick() }
        }
    }) {
        Span({ classes("module-editor-icon-btn-icon") }) { Text(icon) }
        Span({ classes("module-editor-icon-btn-label") }) { Text(label) }
    }
}

internal enum class EditorActionStyle(
    val cssClass: String,
) {
    PrimarySolid("btn-primary"),
    Success("btn-success"),
    PrimaryOutline("btn-outline-primary"),
    SecondaryOutline("btn-outline-secondary"),
    DangerOutline("btn-outline-danger"),
}
