package com.sbrf.lt.platform.composeui.module_editor

import kotlinx.browser.window
import kotlinx.coroutines.launch

internal class ModuleEditorPageDraftBindings(
    private val context: ModuleEditorPageBindingContext,
) {
    fun updateCreateModuleCode(value: String) {
        context.updateState { context.store.updateCreateModuleCode(it, value) }
    }

    fun updateCreateModuleTitle(value: String) {
        context.updateState { context.store.updateCreateModuleTitle(it, value) }
    }

    fun updateCreateModuleDescription(value: String) {
        context.updateState { context.store.updateCreateModuleDescription(it, value) }
    }

    fun updateCreateModuleTags(value: String) {
        context.updateState { context.store.updateCreateModuleTagsText(it, value) }
    }

    fun updateCreateModuleHidden(value: Boolean) {
        context.updateState { context.store.updateCreateModuleHiddenFromUi(it, value) }
    }

    fun updateCreateModuleConfig(value: String) {
        context.updateState { context.store.updateCreateModuleConfigText(it, value) }
    }

    fun restoreTemplate() {
        context.updateState { context.store.restoreCreateModuleTemplate(it) }
    }

    fun closeCreateModuleDialog() {
        context.updateState { context.store.closeCreateModuleDialog(it) }
    }

    fun createModule() {
        context.scope.launch {
            val creatingState = context.store.beginAction(context.currentState(), "create")
            val nextState = context.store.createDatabaseModule(creatingState, context.currentRoute())
            context.setState(nextState)
            context.setCurrentRoute(
                context.currentRoute().copy(
                    moduleId = nextState.selectedModuleId,
                    includeHidden = context.currentRoute().includeHidden || nextState.session?.module?.hiddenFromUi == true,
                    openCreateDialog = false,
                ),
            )
        }
    }

    fun refreshFromConfig() {
        context.scope.launch {
            val syncingState = context.store.startConfigFormSync(context.currentState())
            context.setState(context.store.syncConfigFormFromConfigDraft(syncingState))
        }
    }

    fun applyFormState(nextFormState: ConfigFormStateDto) {
        context.scope.launch {
            val syncingState = context.store.startConfigFormSync(context.currentState())
            context.setState(context.store.applyConfigForm(syncingState, nextFormState))
        }
    }

    fun selectSql(path: String) {
        context.updateState { context.store.selectSqlResource(it, path) }
    }

    fun updateSql(path: String, value: String) {
        context.updateState { context.store.updateSqlText(it, path, value) }
    }

    fun createSql() {
        val rawName = window.prompt("Введите имя SQL-ресурса:")
        if (!rawName.isNullOrBlank()) {
            context.updateState { context.store.createSqlResource(it, rawName) }
        }
    }

    fun renameSql() {
        val currentPath = context.currentState().selectedSqlPath ?: return
        val rawName = window.prompt("Введите новое имя SQL-ресурса:", currentPath)
        if (!rawName.isNullOrBlank()) {
            context.scope.launch {
                context.setState(context.store.renameSqlResource(context.currentState(), rawName))
            }
        }
    }

    fun deleteSql() {
        val currentPath = context.currentState().selectedSqlPath ?: return
        if (window.confirm("Удалить SQL-ресурс '$currentPath'?")) {
            context.updateState { context.store.deleteSqlResource(it) }
        }
    }

    fun updateConfigDraft(value: String) {
        context.updateState { context.store.updateConfigText(it, value) }
    }

    fun updateMetadataTitle(value: String) {
        context.updateState { context.store.updateMetadataTitle(it, value) }
    }

    fun updateMetadataDescription(value: String) {
        context.updateState { context.store.updateMetadataDescription(it, value) }
    }

    fun updateMetadataTags(value: List<String>) {
        context.updateState { context.store.updateMetadataTags(it, value) }
    }

    fun updateMetadataHidden(value: Boolean) {
        context.updateState { context.store.updateMetadataHiddenFromUi(it, value) }
    }

    fun selectTab(tab: ModuleEditorTab) {
        context.updateState { context.store.selectTab(it, tab) }
    }
}
