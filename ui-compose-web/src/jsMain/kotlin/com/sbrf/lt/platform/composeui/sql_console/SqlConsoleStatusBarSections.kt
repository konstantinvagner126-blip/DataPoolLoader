package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleWorkingStatusBar(
    state: SqlConsolePageState,
    execution: SqlConsoleExecutionResponse?,
    activeOutputTab: String,
    activeDataView: String,
    selectedStatementIndex: Int,
    statementResults: List<SqlConsoleStatementResult>,
    selectedShard: String?,
    currentPage: Int,
) {
    val items = buildSqlConsoleWorkingStatusItems(
        state = state,
        execution = execution,
        activeOutputTab = activeOutputTab,
        activeDataView = activeDataView,
        selectedStatementIndex = selectedStatementIndex,
        statementResults = statementResults,
        selectedShard = selectedShard,
        currentPage = currentPage,
    )
    Div({ classes("sql-working-status-bar") }) {
        items.forEach { item ->
            Div({
                classes("sql-working-status-item")
                classes("sql-working-status-item-${item.tone}")
            }) {
                Span({ classes("sql-working-status-item-label") }) { Text(item.label) }
                Span({ classes("sql-working-status-item-value") }) { Text(item.value) }
            }
        }
    }
}

private data class SqlConsoleWorkingStatusItem(
    val label: String,
    val value: String,
    val tone: String = "neutral",
)

private fun buildSqlConsoleWorkingStatusItems(
    state: SqlConsolePageState,
    execution: SqlConsoleExecutionResponse?,
    activeOutputTab: String,
    activeDataView: String,
    selectedStatementIndex: Int,
    statementResults: List<SqlConsoleStatementResult>,
    selectedShard: String?,
    currentPage: Int,
): List<SqlConsoleWorkingStatusItem> {
    val selectedSources = state.selectedSourceNames.size
    val selectedSourceValue = when (selectedSources) {
        0 -> "нет выбранных"
        1 -> "1 выбран"
        else -> "$selectedSources выбрано"
    }
    val totalStatements = statementResults.size
    return buildList {
        add(
            SqlConsoleWorkingStatusItem(
                label = "Источники",
                value = selectedSourceValue,
                tone = if (selectedSources == 0) "warning" else "neutral",
            ),
        )
        add(
            SqlConsoleWorkingStatusItem(
                label = "Режим",
                value = translateTransactionMode(state.transactionMode),
            ),
        )
        add(
            SqlConsoleWorkingStatusItem(
                label = "Защита",
                value = if (state.strictSafetyEnabled) "включена" else "выключена",
                tone = if (state.strictSafetyEnabled) "info" else "neutral",
            ),
        )
        add(
            SqlConsoleWorkingStatusItem(
                label = "Вид",
                value = buildSqlConsoleWorkingResultContext(
                    activeOutputTab = activeOutputTab,
                    activeDataView = activeDataView,
                    selectedStatementIndex = selectedStatementIndex,
                    totalStatements = totalStatements,
                    selectedShard = selectedShard,
                    currentPage = currentPage,
                ),
            ),
        )
        add(buildSqlConsoleWorkingExecutionItem(execution))
    }
}

private fun buildSqlConsoleWorkingResultContext(
    activeOutputTab: String,
    activeDataView: String,
    selectedStatementIndex: Int,
    totalStatements: Int,
    selectedShard: String?,
    currentPage: Int,
): String {
    val normalizedDataView = normalizeSqlResultDataView(activeDataView)
    val baseView = when {
        activeOutputTab == "status" -> "Статусы"
        normalizedDataView == SQL_RESULT_VIEW_DIFF -> "Diff"
        normalizedDataView == SQL_RESULT_VIEW_SOURCE -> "По источникам"
        else -> "Общий grid"
    }
    val statementPart = if (totalStatements > 1) {
        " • stmt ${selectedStatementIndex + 1}/$totalStatements"
    } else {
        ""
    }
    val shardPart = if (activeOutputTab == "data" && selectedShard != null && normalizedDataView == SQL_RESULT_VIEW_SOURCE) {
        " • $selectedShard"
    } else {
        ""
    }
    val pagePart = if (activeOutputTab == "data" && normalizedDataView != SQL_RESULT_VIEW_DIFF) {
        " • p.$currentPage"
    } else {
        ""
    }
    return baseView + statementPart + shardPart + pagePart
}

private fun buildSqlConsoleWorkingExecutionItem(
    execution: SqlConsoleExecutionResponse?,
): SqlConsoleWorkingStatusItem {
    if (execution == null) {
        return SqlConsoleWorkingStatusItem(
            label = "Выполнение",
            value = "ожидание",
        )
    }
    return when {
        execution.transactionState == "PENDING_COMMIT" ->
            SqlConsoleWorkingStatusItem("Выполнение", "ожидает Commit", "warning")
        execution.transactionState == "COMMITTED" ->
            SqlConsoleWorkingStatusItem("Выполнение", "commit", "success")
        execution.transactionState == "ROLLED_BACK" ->
            SqlConsoleWorkingStatusItem("Выполнение", "rollback", "warning")
        execution.transactionState == "ROLLED_BACK_BY_TIMEOUT" ->
            SqlConsoleWorkingStatusItem("Выполнение", "rollback по timeout", "danger")
        execution.transactionState == "ROLLED_BACK_BY_OWNER_LOSS" ->
            SqlConsoleWorkingStatusItem("Выполнение", "rollback из-за потери owner", "danger")
        execution.status.equals("RUNNING", ignoreCase = true) ->
            SqlConsoleWorkingStatusItem("Выполнение", "выполняется", "info")
        execution.status.equals("FAILED", ignoreCase = true) ->
            SqlConsoleWorkingStatusItem("Выполнение", "ошибка", "danger")
        execution.status.equals("SUCCESS", ignoreCase = true) ->
            SqlConsoleWorkingStatusItem("Выполнение", "успех", "success")
        execution.status.equals("CANCELLED", ignoreCase = true) ->
            SqlConsoleWorkingStatusItem("Выполнение", "отменено", "warning")
        else -> SqlConsoleWorkingStatusItem("Выполнение", execution.status)
    }
}
