package com.sbrf.lt.platform.composeui.sql_console

internal class SqlConsoleEditorBindings(
    private val context: SqlConsolePageBindingContext,
    private val executionBindings: SqlConsoleExecutionBindings,
) {
    fun focusEditor() {
        context.focusEditor()
    }

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
        val initialSelection = readSqlEditorSelection(monacoEditor)
        context.updateUiState {
            it.copy(
                editorInstance = monacoEditor,
                selectedSqlText = initialSelection.sql,
                selectedSqlLineCount = initialSelection.lineCount,
            )
        }
        monacoEditor.onDidChangeCursorPosition { event ->
            context.updateUiState { current -> current.copy(editorCursorLine = event.position.lineNumber as Int) }
        }
        monacoEditor.onDidChangeCursorSelection {
            val selection = readSqlEditorSelection(monacoEditor)
            context.updateUiState { current ->
                current.copy(
                    selectedSqlText = selection.sql,
                    selectedSqlLineCount = selection.lineCount,
                )
            }
        }
        monacoEditor.onDidFocusEditorText {
            context.updateUiState { current -> current.copy(editorFocused = true) }
        }
        monacoEditor.onDidBlurEditorText {
            context.updateUiState { current -> current.copy(editorFocused = false) }
        }
        registerSqlConsoleEditorShortcuts(
            editor = monacoEditor,
            onRunCurrent = executionBindings::runCurrent,
            onRunSelection = executionBindings::runSelection,
            onRunAll = executionBindings::runAll,
            onFormat = executionBindings::formatSql,
            onStop = executionBindings::stop,
            onShowData = { selectOutputTab("data") },
            onShowStatus = { selectOutputTab("status") },
            onPreviousStatement = { shiftStatement(-1) },
            onNextStatement = { shiftStatement(1) },
            onPreviousShard = { shiftShard(-1) },
            onNextShard = { shiftShard(1) },
            onPreviousPage = { shiftPage(-1) },
            onNextPage = { shiftPage(1) },
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

    fun selectDataView(view: String) {
        context.updateUiState {
            it.copy(
                activeDataView = view,
                selectedResultShard = null,
                currentDataPage = 1,
            )
        }
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

    private fun shiftStatement(delta: Int) {
        val statementResults = context.currentState().currentExecution?.result.statementResultsOrSelf()
        if (statementResults.isEmpty()) {
            return
        }
        val currentIndex = context.currentUiState().selectedStatementIndex
        selectStatement((currentIndex + delta).coerceIn(0, statementResults.lastIndex))
    }

    private fun shiftShard(delta: Int) {
        val successfulShards = context.exportableResult()
            ?.takeIf {
                context.currentUiState().activeOutputTab == "data" &&
                    context.currentUiState().activeDataView == "grid"
            }
            ?.shardResults
            ?.filter { it.status.equals("SUCCESS", ignoreCase = true) && it.rows.isNotEmpty() }
            .orEmpty()
        if (successfulShards.size <= 1) {
            return
        }
        val currentShardName = context.currentUiState().selectedResultShard
        val currentIndex = successfulShards.indexOfFirst { it.shardName == currentShardName }.takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + delta).coerceIn(0, successfulShards.lastIndex)
        selectShard(successfulShards[nextIndex].shardName)
    }

    private fun shiftPage(delta: Int) {
        val result = context.exportableResult()
            ?.takeIf {
                context.currentUiState().activeOutputTab == "data" &&
                    context.currentUiState().activeDataView == "grid" &&
                    it.statementType == "RESULT_SET"
            }
            ?: return
        val selectedShard = result.shardResults
            .filter { it.status.equals("SUCCESS", ignoreCase = true) && it.rows.isNotEmpty() }
            .firstOrNull { it.shardName == context.currentUiState().selectedResultShard }
            ?: return
        val totalPages = maxOf(1, (selectedShard.rowCount + context.currentState().pageSize - 1) / context.currentState().pageSize)
        val currentPage = context.currentUiState().currentDataPage
        selectPage((currentPage + delta).coerceIn(1, totalPages))
    }
}
