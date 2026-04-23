package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleEditorBindings(
    private val context: SqlConsolePageBindingContext,
    private val executionBindings: SqlConsoleExecutionBindings,
) {
    fun insertFavoriteObject(sql: String) {
        insertSqlText(
            editor = context.currentUiState().editorInstance,
            text = sql,
            currentValue = context.currentState().draftSql,
            onFallback = { nextSql ->
                context.updateState { context.store.updateDraftSql(it, nextSql) }
            },
        )
    }

    fun onEditorReady(editor: Any) {
        val monacoEditor = editor.asDynamic()
        context.updateUiState { it.copy(editorInstance = monacoEditor) }
        monacoEditor.onDidChangeCursorPosition { event ->
            context.updateUiState { current -> current.copy(editorCursorLine = event.position.lineNumber as Int) }
        }
        registerSqlConsoleEditorShortcuts(
            editor = monacoEditor,
            onRun = executionBindings::runAll,
            onRunCurrent = executionBindings::runCurrent,
            onFormat = executionBindings::formatSql,
            onStop = executionBindings::stop,
        )
    }

    fun updateDraftSql(next: String) {
        context.updateState { context.store.updateDraftSql(it, next) }
    }

    fun updatePageSize(nextPageSize: Int) {
        context.updateState { context.store.updatePageSize(it, nextPageSize) }
    }

    fun selectStatement(index: Int) {
        context.updateUiState {
            it.copy(
                selectedStatementIndex = index,
                selectedResultShard = null,
                currentDataPage = 1,
            )
        }
    }

    fun selectOutputTab(tab: String) {
        context.updateUiState { it.copy(activeOutputTab = tab) }
    }

    fun selectShard(shardName: String?) {
        context.updateUiState {
            it.copy(
                selectedResultShard = shardName,
                currentDataPage = 1,
            )
        }
    }

    fun selectPage(page: Int) {
        context.updateUiState { it.copy(currentDataPage = page) }
    }
}
