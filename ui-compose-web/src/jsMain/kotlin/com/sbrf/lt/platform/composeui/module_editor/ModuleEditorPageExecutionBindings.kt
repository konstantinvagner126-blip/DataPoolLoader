package com.sbrf.lt.platform.composeui.module_editor

import kotlinx.browser.window
import kotlinx.coroutines.launch

internal class ModuleEditorPageExecutionBindings(
    private val context: ModuleEditorPageBindingContext,
) {
    fun runModule() {
        context.scope.launch {
            val runningState = context.store.beginAction(context.currentState(), "run")
            val nextState = if (context.currentRoute().storage == "database") {
                context.store.runDatabaseModule(runningState)
            } else {
                context.store.runFilesModule(runningState)
            }
            context.setState(nextState)
            context.refreshModuleCatalog()
            nextState.selectedModuleId?.let { moduleId ->
                context.refreshEditorRunPanel(moduleId)
            }
        }
    }

    fun saveModule() {
        context.scope.launch {
            val savingState = context.store.beginAction(context.currentState(), "save")
            val nextState = if (context.currentRoute().storage == "database") {
                context.store.saveDatabaseWorkingCopy(savingState, context.currentRoute())
            } else {
                context.store.saveFilesModule(savingState, context.currentRoute())
            }
            context.setState(nextState)
        }
    }

    fun discardWorkingCopy() {
        if (window.confirm("Сбросить личный черновик? Несохраненные изменения будут потеряны.")) {
            context.scope.launch {
                val discardState = context.store.beginAction(context.currentState(), "discard")
                context.setState(context.store.discardDatabaseWorkingCopy(discardState, context.currentRoute()))
            }
        }
    }

    fun publishWorkingCopy() {
        if (window.confirm("Опубликовать черновик как новую ревизию? После публикации личный черновик будет удален.")) {
            context.scope.launch {
                val publishState = context.store.beginAction(context.currentState(), "publish")
                context.setState(context.store.publishDatabaseWorkingCopy(publishState, context.currentRoute()))
            }
        }
    }

    fun reloadModule() {
        val moduleId = context.currentState().selectedModuleId
        if (!moduleId.isNullOrBlank()) {
            context.scope.launch {
                context.setState(context.store.startLoading(context.currentState()))
                context.setState(context.store.selectModule(context.currentState(), context.currentRoute(), moduleId))
            }
        }
    }
}
