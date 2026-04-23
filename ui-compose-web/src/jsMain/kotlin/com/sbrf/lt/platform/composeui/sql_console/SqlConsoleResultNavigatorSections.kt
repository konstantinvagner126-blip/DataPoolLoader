package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleResultNavigator(
    statementResults: List<SqlConsoleStatementResult>,
    selectedStatementIndex: Int,
    activeTab: String,
    activeDataView: String,
    result: SqlConsoleQueryResult?,
    selectedShard: String?,
    currentPage: Int,
    pageSize: Int,
    onSelectStatement: (Int) -> Unit,
    onSelectDataView: (String) -> Unit,
    onSelectShard: (String?) -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    if (statementResults.isEmpty() && result == null) {
        return
    }

    val normalizedStatementIndex = selectedStatementIndex.coerceIn(0, statementResults.lastIndex.coerceAtLeast(0))
    val activeStatement = statementResults.getOrNull(normalizedStatementIndex)
    val showDataNavigation = activeTab == "data" && result?.statementType == "RESULT_SET"
    val showGridNavigation = showDataNavigation && activeDataView == "grid"
    val successfulShards = result
        ?.takeIf { showGridNavigation }
        ?.shardResults
        ?.filter { it.status.equals("SUCCESS", ignoreCase = true) && it.rows.isNotEmpty() }
        .orEmpty()
    val activeShard = successfulShards.firstOrNull { it.shardName == selectedShard } ?: successfulShards.firstOrNull()
    val totalPages = activeShard?.let { maxOf(1, (it.rowCount + pageSize - 1) / pageSize) } ?: 1
    val normalizedPage = currentPage.coerceIn(1, totalPages)

    Div({ classes("sql-result-navigator") }) {
        Div({ classes("sql-result-navigator-head") }) {
            Div({ classes("sql-result-navigator-title") }) {
                Text(buildNavigatorTitle(activeTab, activeDataView, activeStatement, normalizedStatementIndex))
            }
            Div({ classes("sql-result-navigator-note") }) {
                Text(buildNavigatorNote(activeTab, activeDataView, successfulShards.size, activeShard?.shardName, normalizedPage, totalPages))
            }
        }
        Div({ classes("sql-result-navigator-groups") }) {
            if (statementResults.size > 1) {
                SqlResultNavigatorGroup(title = "Statement") {
                    statementResults.forEachIndexed { index, statement ->
                        SqlResultNavigatorButton(
                            active = index == normalizedStatementIndex,
                            onClick = { onSelectStatement(index) },
                        ) {
                            Text("#${index + 1} ${statement.statementKeyword}")
                        }
                    }
                }
            }
            if (showDataNavigation) {
                SqlResultNavigatorGroup(title = "Режим") {
                    SqlResultNavigatorButton(
                        active = activeDataView == "grid",
                        onClick = { onSelectDataView("grid") },
                    ) {
                        Text("Grid")
                    }
                    SqlResultNavigatorButton(
                        active = activeDataView == "diff",
                        onClick = { onSelectDataView("diff") },
                    ) {
                        Text("Diff")
                    }
                }
            }
            if (successfulShards.size > 1) {
                SqlResultNavigatorGroup(title = "Source") {
                    successfulShards.forEach { shard ->
                        SqlResultNavigatorButton(
                            active = shard.shardName == activeShard?.shardName,
                            onClick = { onSelectShard(shard.shardName) },
                        ) {
                            Text("${shard.shardName} (${shard.rowCount})")
                        }
                    }
                }
            }
            if (totalPages > 1) {
                SqlResultNavigatorGroup(title = "Страница") {
                    buildNavigatorPages(normalizedPage, totalPages).forEach { page ->
                        if (page == null) {
                            Span({ classes("sql-result-navigator-ellipsis") }) { Text("…") }
                        } else {
                            SqlResultNavigatorButton(
                                active = page == normalizedPage,
                                onClick = { onSelectPage(page) },
                            ) {
                                Text(page.toString())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SqlResultNavigatorGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Div({ classes("sql-result-navigator-group") }) {
        Div({ classes("sql-result-navigator-group-title") }) { Text(title) }
        Div({ classes("sql-result-navigator-group-buttons") }) {
            content()
        }
    }
}

@Composable
private fun SqlResultNavigatorButton(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Button(attrs = {
        classes("btn", "btn-sm", "sql-result-navigator-button")
        if (active) {
            classes("btn-dark")
        } else {
            classes("btn-outline-secondary")
        }
        attr("type", "button")
        onClick { onClick() }
    }) {
        content()
    }
}

private fun buildNavigatorTitle(
    activeTab: String,
    activeDataView: String,
    activeStatement: SqlConsoleStatementResult?,
    statementIndex: Int,
): String {
    val statementLabel = activeStatement?.statementKeyword?.takeIf { it.isNotBlank() } ?: "SQL"
    val viewLabel = when {
        activeTab == "status" -> "Статусы"
        activeDataView == "diff" -> "Diff"
        else -> "Данные"
    }
    return "$viewLabel для statement #${statementIndex + 1} $statementLabel"
}

private fun buildNavigatorNote(
    activeTab: String,
    activeDataView: String,
    shardCount: Int,
    activeShardName: String?,
    currentPage: Int,
    totalPages: Int,
): String =
    when {
        activeTab == "status" -> "Выбери statement, чтобы смотреть итог по всем source без переключения контекста."
        activeDataView == "diff" -> "Compare-режим показывает расхождения по всем source относительно baseline, не смешивая их с обычной grid."
        shardCount == 0 -> "Текущий statement не вернул табличные данные по source."
        totalPages > 1 && activeShardName != null ->
            "Активный source: $activeShardName. Страница $currentPage из $totalPages."
        activeShardName != null ->
            "Активный source: $activeShardName. Данные показываются отдельно по каждому source."
        else -> "Данные показываются отдельно по каждому source."
    }

private fun buildNavigatorPages(
    currentPage: Int,
    totalPages: Int,
): List<Int?> {
    if (totalPages <= 7) {
        return (1..totalPages).map { it }
    }
    val pages = linkedSetOf(1, totalPages, currentPage - 1, currentPage, currentPage + 1)
        .filter { it in 1..totalPages }
        .sorted()
    return buildList {
        var last: Int? = null
        pages.forEach { page ->
            if (last != null && page - last!! > 1) {
                add(null)
            }
            add(page)
            last = page
        }
    }
}
