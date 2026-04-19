package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.format.formatDateTime
import com.sbrf.lt.platform.composeui.foundation.format.formatDuration
import com.sbrf.lt.platform.composeui.foundation.format.formatDurationMillis
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr
import org.jetbrains.compose.web.dom.Ul

@Composable
internal fun SqlConsoleHeroArt() {
    Div({ classes("sql-console-stage") }) {
        Div({ classes("sql-console-node", "sql-console-node-sources") }) { Text("SOURCES") }
        Div({ classes("sql-console-node", "sql-console-node-check") }) { Text("CHECK") }
        Div({ classes("sql-console-node", "sql-console-node-sql") }) { Text("SQL") }

        Div({ classes("sql-console-line", "sql-console-line-left-top") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-left-top") })
        }
        Div({ classes("sql-console-line", "sql-console-line-left-middle") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-left-middle") })
        }
        Div({ classes("sql-console-line", "sql-console-line-left-bottom") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-left-bottom") })
        }

        Div({ classes("sql-console-hub") }) {
            Div({ classes("merge-title") }) { Text("QUERY") }
            Div({ classes("merge-subtitle") }) { Text("RUNNER") }
        }

        Div({ classes("sql-console-line", "sql-console-line-right-top") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-right-top") })
        }
        Div({ classes("sql-console-line", "sql-console-line-right-bottom") }) {
            Span({ classes("sql-console-dot", "sql-console-dot-right-bottom") })
        }

        Div({ classes("sql-console-node", "sql-console-node-results") }) { Text("RESULTS") }
        Div({ classes("sql-console-node", "sql-console-node-status") }) { Text("STATUS") }
    }
}

@Composable
internal fun QueryLibraryBlock(
    state: SqlConsolePageState,
    selectedRecentQuery: String,
    selectedFavoriteQuery: String,
    onRecentSelected: (String) -> Unit,
    onFavoriteSelected: (String) -> Unit,
    onApplyRecent: () -> Unit,
    onApplyFavorite: () -> Unit,
    onRememberFavorite: () -> Unit,
    onRemoveFavorite: () -> Unit,
    onClearRecent: () -> Unit,
    onStrictSafetyToggle: () -> Unit,
    onAutoCommitToggle: (Boolean) -> Unit,
) {
    Div({ classes("sql-query-library", "mb-3") }) {
        Div({ classes("sql-query-library-row") }) {
            Div({ classes("sql-query-library-block") }) {
                Label(attrs = {
                    classes("small", "text-secondary", "mb-1")
                    attr("for", "composeRecentQueries")
                }) { Text("Последние запросы") }
                Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                    Select(attrs = {
                        id("composeRecentQueries")
                        classes("form-select", "form-select-sm", "sql-recent-query-select")
                        onChange { onRecentSelected(it.value ?: "") }
                    }) {
                        Option(value = "") { Text(if (state.recentQueries.isEmpty()) "История пока пуста" else "Выбери запрос") }
                        state.recentQueries.forEach { query ->
                            Option(value = query, attrs = { if (selectedRecentQuery == query) selected() }) {
                                Text(query.take(120))
                            }
                        }
                    }
                    Button(attrs = {
                        classes("btn", "btn-outline-secondary", "btn-sm")
                        attr("type", "button")
                        if (selectedRecentQuery.isBlank()) {
                            disabled()
                        }
                        onClick { onApplyRecent() }
                    }) { Text("Подставить") }
                    Button(attrs = {
                        classes("btn", "btn-outline-secondary", "btn-sm")
                        attr("type", "button")
                        onClick { onClearRecent() }
                    }) { Text("Очистить") }
                }
            }
            Div({ classes("sql-query-library-block") }) {
                Label(attrs = {
                    classes("small", "text-secondary", "mb-1")
                    attr("for", "composeFavoriteQueries")
                }) { Text("Избранные запросы") }
                Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                    Select(attrs = {
                        id("composeFavoriteQueries")
                        classes("form-select", "form-select-sm", "sql-recent-query-select")
                        onChange { onFavoriteSelected(it.value ?: "") }
                    }) {
                        Option(value = "") { Text(if (state.favoriteQueries.isEmpty()) "Избранное пока пусто" else "Выбери запрос") }
                        state.favoriteQueries.forEach { query ->
                            Option(value = query, attrs = { if (selectedFavoriteQuery == query) selected() }) {
                                Text(query.take(120))
                            }
                        }
                    }
                    Button(attrs = {
                        classes("btn", "btn-outline-secondary", "btn-sm")
                        attr("type", "button")
                        if (selectedFavoriteQuery.isBlank()) {
                            disabled()
                        }
                        onClick { onApplyFavorite() }
                    }) { Text("Подставить") }
                    Button(attrs = {
                        classes("btn", "btn-outline-primary", "btn-sm")
                        attr("type", "button")
                        onClick { onRememberFavorite() }
                    }) { Text("В избранное") }
                    Button(attrs = {
                        classes("btn", "btn-outline-danger", "btn-sm")
                        attr("type", "button")
                        if (selectedFavoriteQuery.isBlank()) {
                            disabled()
                        }
                        onClick { onRemoveFavorite() }
                    }) { Text("Убрать") }
                }
            }
        }
        Div({ classes("sql-query-library-block") }) {
            Label(attrs = { classes("d-flex", "align-items-center", "gap-2", "small", "text-secondary", "mb-0") }) {
                Input(type = InputType.Checkbox, attrs = {
                    classes("form-check-input")
                    if (state.strictSafetyEnabled) {
                        attr("checked", "checked")
                    }
                    onClick { onStrictSafetyToggle() }
                })
                Span { Text("Read-only") }
            }
        }
        Div({ classes("sql-query-library-block") }) {
            Label(attrs = { classes("d-flex", "align-items-center", "gap-2", "small", "text-secondary", "mb-0") }) {
                Input(type = InputType.Checkbox, attrs = {
                    classes("form-check-input")
                    if (state.transactionMode == "AUTO_COMMIT") {
                        attr("checked", "checked")
                    }
                    onClick { onAutoCommitToggle(state.transactionMode != "AUTO_COMMIT") }
                })
                Span { Text("Autocommit") }
            }
        }
    }
}

@Composable
internal fun SqlFavoriteObjectsBlock(
    favorites: List<SqlConsoleFavoriteObject>,
    onInsert: (SqlConsoleFavoriteObject) -> Unit,
    onInsertSelect: (SqlConsoleFavoriteObject) -> Unit,
    onInsertCount: (SqlConsoleFavoriteObject) -> Unit,
    onOpenMetadata: (SqlConsoleFavoriteObject) -> Unit,
    onRemove: (SqlConsoleFavoriteObject) -> Unit,
) {
    if (favorites.isEmpty()) {
        return
    }
    Div({ classes("sql-query-library", "mb-3") }) {
        Div({ classes("d-flex", "align-items-center", "justify-content-between", "gap-3", "mb-2") }) {
            Div({ classes("panel-title", "mb-0") }) { Text("Избранные объекты") }
            Div({ classes("small", "text-secondary") }) {
                Text("Быстрая вставка имен и готовых SQL-шаблонов в редактор.")
            }
        }
        Div({ classes("sql-favorite-objects-grid") }) {
            favorites.forEach { favorite ->
                Div({ classes("sql-favorite-object-card") }) {
                    Div({ classes("sql-favorite-object-meta") }) {
                        Div({ classes("sql-favorite-object-name") }) {
                            Text(favorite.qualifiedName())
                        }
                        Div({ classes("sql-favorite-object-note") }) {
                            Text("${favorite.sourceName} • ${translateFavoriteObjectType(favorite.objectType)}")
                        }
                    }
                    Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                        Button(attrs = {
                            classes("btn", "btn-outline-dark", "btn-sm")
                            attr("type", "button")
                            onClick { onInsert(favorite) }
                        }) { Text("Вставить") }
                        Button(attrs = {
                            classes("btn", "btn-dark", "btn-sm")
                            attr("type", "button")
                            onClick { onInsertSelect(favorite) }
                        }) { Text(if (supportsFavoriteRowPreview(favorite)) "SELECT *" else "В SQL") }
                        if (supportsFavoriteRowPreview(favorite)) {
                            Button(attrs = {
                                classes("btn", "btn-outline-dark", "btn-sm")
                                attr("type", "button")
                                onClick { onInsertCount(favorite) }
                            }) { Text("COUNT(*)") }
                        }
                        Button(attrs = {
                            classes("btn", "btn-outline-secondary", "btn-sm")
                            attr("type", "button")
                            onClick { onOpenMetadata(favorite) }
                        }) { Text("Метаданные") }
                        Button(attrs = {
                            classes("btn", "btn-outline-danger", "btn-sm")
                            attr("type", "button")
                            onClick { onRemove(favorite) }
                        }) { Text("Убрать") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CommandGuardrail(
    analysis: SqlStatementAnalysis,
    strictSafetyEnabled: Boolean,
) {
    val cssClass = when {
        analysis.keyword == "SQL" -> "sql-guardrail sql-guardrail-neutral"
        strictSafetyEnabled && !analysis.readOnly -> "sql-guardrail sql-guardrail-danger"
        analysis.dangerous -> "sql-guardrail sql-guardrail-danger"
        !analysis.readOnly -> "sql-guardrail sql-guardrail-warning"
        else -> "sql-guardrail sql-guardrail-safe"
    }
    val text = when {
        analysis.keyword == "SQL" -> "Текущий запрос не определен. Введи SQL, чтобы UI показал тип команды и предупредил о потенциально опасных операциях."
        strictSafetyEnabled && !analysis.readOnly -> "Строгая защита включена. Команда ${analysis.keyword} будет заблокирована до отключения этого режима."
        analysis.dangerous -> "Команда ${analysis.keyword} считается потенциально опасной. Перед запуском перепроверь SQL и выбранные источники."
        !analysis.readOnly -> "Команда ${analysis.keyword} может изменить данные или структуру на выбранных источниках."
        else -> "Команда ${analysis.keyword} распознана как read-only."
    }
    Div({ classes(*cssClass.split(" ").toTypedArray()) }) {
        Text(text)
    }
}

@Composable
internal fun SqlEditorIdeBlock(
    outlineItems: List<SqlScriptOutlineItem>,
    currentLine: Int,
    onJumpToLine: (Int) -> Unit,
) {
    Div({ classes("sql-query-library", "mb-3") }) {
        Div({ classes("sql-query-library-block") }) {
            Div({ classes("d-flex", "justify-content-between", "align-items-center", "gap-3", "mb-2") }) {
                Div({ classes("small", "text-secondary") }) { Text("Script outline") }
                Div({ classes("small", "text-secondary") }) { Text("Statement-ов: ${outlineItems.size}") }
            }
            if (outlineItems.isEmpty()) {
                Div({ classes("small", "text-secondary") }) {
                    Text("Введи SQL, чтобы получить карту statement-ов и быстро прыгать по строкам.")
                }
            } else {
                Div({ classes("sql-script-outline") }) {
                    outlineItems.forEach { item ->
                        Button(attrs = {
                            classes(
                                "btn",
                                "btn-sm",
                                "sql-script-outline-item",
                                if (currentLine in item.startLine..item.endLine) "btn-dark" else "btn-outline-secondary",
                            )
                            attr("type", "button")
                            onClick { onJumpToLine(item.startLine) }
                        }) {
                            Div({ classes("d-flex", "justify-content-between", "align-items-start", "gap-3", "w-100") }) {
                                Span({ classes("sql-script-outline-item-main") }) {
                                    Text("#${item.index} ${item.keyword} · строки ${item.startLine}-${item.endLine}")
                                }
                                StatementRiskBadge(item)
                            }
                            Span({ classes("sql-script-outline-item-preview") }) {
                                Text(item.preview)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatementRiskBadge(item: SqlScriptOutlineItem) {
    val cssClass = when {
        item.keyword == "SQL" -> "sql-statement-risk-badge sql-statement-risk-neutral"
        item.dangerous -> "sql-statement-risk-badge sql-statement-risk-danger"
        !item.readOnly -> "sql-statement-risk-badge sql-statement-risk-warning"
        else -> "sql-statement-risk-badge sql-statement-risk-safe"
    }
    val text = when {
        item.keyword == "SQL" -> "SQL"
        item.dangerous -> "Опасно"
        !item.readOnly -> "Меняет данные"
        else -> "Read-only"
    }
    Span({ classes(*cssClass.split(" ").toTypedArray()) }) { Text(text) }
}

@Composable
internal fun StatementSelectionBlock(
    statementResults: List<SqlConsoleStatementResult>,
    selectedStatementIndex: Int,
    onSelectStatement: (Int) -> Unit,
) {
    if (statementResults.size <= 1) {
        return
    }

    Div({ classes("sql-query-library", "mb-3") }) {
        Div({ classes("small", "text-secondary", "mb-2") }) {
            Text("Скрипт содержит ${statementResults.size} statement-ов. Выбери statement, для которого показывать данные, статусы и экспорт.")
        }
        Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
            statementResults.forEachIndexed { index, statement ->
                Button(attrs = {
                    classes(
                        "btn",
                        "btn-sm",
                        if (index == selectedStatementIndex) "btn-dark" else "btn-outline-secondary",
                    )
                    attr("type", "button")
                    onClick { onSelectStatement(index) }
                }) {
                    Text("#${index + 1} ${statement.statementKeyword}")
                }
            }
        }
    }
}

@Composable
internal fun ExecutionStatusStrip(
    execution: SqlConsoleExecutionResponse?,
    runningClockTick: Int,
) {
    val isRunning = execution?.status.equals("RUNNING", ignoreCase = true)
    val showLiveDuration = isRunning && runningClockTick >= 0
    val cssClass = when {
        execution == null -> "sql-status-strip"
        execution.status.equals("FAILED", ignoreCase = true) -> "sql-status-strip sql-status-strip-failed"
        execution.status.equals("SUCCESS", ignoreCase = true) -> "sql-status-strip sql-status-strip-success"
        execution.status.equals("CANCELLED", ignoreCase = true) -> "sql-status-strip sql-status-strip-warning"
        else -> "sql-status-strip sql-status-strip-running"
    }
    val text = when {
        execution == null -> "Запрос пока не выполнялся."
        execution.status.equals("RUNNING", ignoreCase = true) && execution.cancelRequested ->
            "Запрос выполняется, отправлена команда на остановку."
        execution.status.equals("RUNNING", ignoreCase = true) ->
            "Сценарий выполняется."
        execution.transactionState == "PENDING_COMMIT" ->
            "Сценарий выполнен и ждет команды Коммит или Роллбек."
        execution.transactionState == "COMMITTED" ->
            "Транзакция зафиксирована."
        execution.transactionState == "ROLLED_BACK" ->
            "Транзакция откатана."
        execution.status.equals("SUCCESS", ignoreCase = true) ->
            "Запрос завершен успешно."
        execution.status.equals("FAILED", ignoreCase = true) ->
            execution.errorMessage ?: "Запрос завершился ошибкой."
        execution.status.equals("CANCELLED", ignoreCase = true) ->
            "Запрос остановлен."
        else -> "Статус запроса: ${execution.status}."
    }
    Div({ classes(*cssClass.split(" ").toTypedArray()) }) {
        Div({ classes("sql-status-strip-content") }) {
            if (isRunning && execution != null) {
                Div({ classes("run-progress-status-wrap") }) {
                    Div({ classes("run-progress-spinner-arrows") }) {
                        Span({ classes("run-progress-spinner-arrow", "run-progress-spinner-arrow-forward") }) { Text("↻") }
                        Span({ classes("run-progress-spinner-arrow", "run-progress-spinner-arrow-backward") }) { Text("↺") }
                    }
                    Div({ classes("sql-status-strip-copy") }) {
                        Div({ classes("sql-status-strip-title") }) { Text(text) }
                        Div({ classes("sql-status-strip-meta") }) {
                            Text(
                                buildString {
                                    append("Старт: ")
                                    append(formatDateTime(execution.startedAt))
                                    append(" • Прошло: ")
                                    append(formatDuration(execution.startedAt, execution.finishedAt, running = showLiveDuration))
                                },
                            )
                        }
                    }
                }
            } else {
                Div({ classes("sql-status-strip-copy") }) {
                    Div({ classes("sql-status-strip-title") }) { Text(text) }
                    if (execution != null) {
                        Div({ classes("sql-status-strip-meta") }) {
                            Text(
                                buildString {
                                    append("Старт: ")
                                    append(formatDateTime(execution.startedAt))
                                    if (execution.transactionState == "PENDING_COMMIT" && execution.transactionShardNames.isNotEmpty()) {
                                        append(" • Открытых транзакций: ")
                                        append(execution.transactionShardNames.joinToString(", "))
                                    }
                                    if (!execution.finishedAt.isNullOrBlank()) {
                                        append(" • Завершение: ")
                                        append(formatDateTime(execution.finishedAt))
                                        append(" • Длительность: ")
                                        append(formatDuration(execution.startedAt, execution.finishedAt))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun QueryOutputPanel(
    execution: SqlConsoleExecutionResponse?,
    result: SqlConsoleQueryResult?,
    pageSize: Int,
    activeTab: String,
    selectedShard: String?,
    currentPage: Int,
    onSelectTab: (String) -> Unit,
    onSelectShard: (String) -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    Div({ classes("sql-output-tabs") }) {
        OutputTabButton(
            label = "Данные",
            active = activeTab == "data",
            enabled = true,
            onClick = { onSelectTab("data") },
        )
        OutputTabButton(
            label = "Статусы",
            active = activeTab == "status",
            enabled = true,
            onClick = { onSelectTab("status") },
        )
    }
    Div({
        classes("sql-output-pane")
        if (activeTab == "data") {
            classes("active")
        }
    }) {
        if (activeTab == "data") {
            SelectResultPane(
                execution = execution,
                result = result,
                pageSize = pageSize,
                selectedShard = selectedShard,
                currentPage = currentPage,
                onSelectShard = onSelectShard,
                onSelectPage = onSelectPage,
            )
        }
    }
    Div({
        classes("sql-output-pane")
        if (activeTab == "status") {
            classes("active")
        }
    }) {
        if (activeTab == "status") {
            StatusResultPane(
                execution = execution,
                result = result,
            )
        }
    }
}

@Composable
internal fun OutputTabButton(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("sql-output-tab")
        if (active) {
            classes("active")
        }
        attr("type", "button")
        if (!enabled) {
            disabled()
        }
        onClick { onClick() }
    }) {
        Text(label)
    }
}

@Composable
internal fun SelectResultPane(
    execution: SqlConsoleExecutionResponse?,
    result: SqlConsoleQueryResult?,
    pageSize: Int,
    selectedShard: String?,
    currentPage: Int,
    onSelectShard: (String) -> Unit,
    onSelectPage: (Int) -> Unit,
) {
    if (execution == null) {
        Div({ classes("text-secondary", "small", "mb-3") }) {
            Text("Пока нет данных для отображения.")
        }
        Div({ classes("sql-result-placeholder") }) {
            Text("Выполни запрос, чтобы увидеть данные со всех shard/source.")
        }
        return
    }

    if (result == null) {
        val message = execution.errorMessage?.takeIf { it.isNotBlank() }
        if (message != null) {
            AlertBanner(message, "danger")
        } else {
            Div({ classes("text-secondary", "small", "mb-3") }) {
                Text("Выполняется запрос...")
            }
            Div({ classes("sql-result-placeholder") }) {
                Text("Ожидается завершение запроса.")
            }
        }
        return
    }

    if (result.statementType != "RESULT_SET") {
        Div({ classes("sql-result-placeholder") }) {
            Text("Команда ${result.statementKeyword} не возвращает табличные данные. Смотри вкладку «Статусы».")
        }
        return
    }

    val successfulShards = result.shardResults.filter { it.status.equals("SUCCESS", ignoreCase = true) && it.rows.isNotEmpty() }
    if (successfulShards.isEmpty()) {
        Div({ classes("sql-result-placeholder") }) {
            Text("Ни один source не вернул данные для отображения.")
        }
        return
    }

    val activeShard = successfulShards.firstOrNull { it.shardName == selectedShard } ?: successfulShards.first()
    val totalPages = maxOf(1, (activeShard.rowCount + pageSize - 1) / pageSize)
    val normalizedPage = currentPage.coerceIn(1, totalPages)
    val startIndex = (normalizedPage - 1) * pageSize
    val endIndexExclusive = minOf(startIndex + pageSize, activeShard.rowCount)
    val visibleRows = activeShard.rows.drop(startIndex).take(pageSize)

    Div({ classes("text-secondary", "small", "mb-3") }) {
        Text("Данные показываются отдельно по каждому source. Лимит на source: ${result.maxRowsPerShard}.")
    }
    Ul({ classes("nav", "nav-tabs", "sql-result-tabs", "mb-3") }) {
        successfulShards.forEach { shard ->
            Li({ classes("nav-item") }) {
                Button(attrs = {
                    classes("nav-link")
                    if (shard.shardName == activeShard.shardName) {
                        classes("active")
                    }
                    attr("type", "button")
                    onClick { onSelectShard(shard.shardName) }
                }) {
                    Text("${shard.shardName} (${shard.rowCount})")
                }
            }
        }
    }
    Div({ classes("small", "text-secondary", "mb-3") }) {
        Text(
            buildString {
                append("Source ")
                append(activeShard.shardName)
                append(". Показано строк: ")
                append(if (activeShard.rowCount == 0) 0 else startIndex + 1)
                append("-")
                append(endIndexExclusive)
                append(" из ")
                append(activeShard.rowCount)
                append(". Страница ")
                append(normalizedPage)
                append(" из ")
                append(totalPages)
                append(".")
                if (activeShard.truncated) {
                    append(" Результат усечен лимитом ${result.maxRowsPerShard} строк на source.")
                }
            },
        )
    }
    Div({ classes("table-responsive") }) {
        Table({ classes("table", "table-sm", "table-striped", "sql-result-table", "mb-0") }) {
            Thead {
                Tr {
                    activeShard.columns.forEach { column ->
                        Th { Text(column) }
                    }
                }
            }
            Tbody {
                visibleRows.forEach { row ->
                    Tr {
                        activeShard.columns.forEach { column ->
                            Td { Text(row[column] ?: "") }
                        }
                    }
                }
            }
        }
    }
    if (totalPages > 1) {
        Div({ classes("sql-pagination-footer") }) {
            Div({ classes("small", "text-secondary") }) {
                Text("Страница $normalizedPage из $totalPages")
            }
            Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                (1..totalPages).forEach { page ->
                    Button(attrs = {
                        classes(
                            "btn",
                            "btn-sm",
                            if (page == normalizedPage) "btn-dark" else "btn-outline-secondary",
                        )
                        attr("type", "button")
                        onClick { onSelectPage(page) }
                    }) {
                        Text(page.toString())
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatusResultPane(
    execution: SqlConsoleExecutionResponse?,
    result: SqlConsoleQueryResult?,
) {
    if (execution == null) {
        Div({ classes("sql-result-placeholder") }) {
            Text("Пока нет результатов для отображения.")
        }
        return
    }

    if (result == null) {
        val message = execution.errorMessage?.takeIf { it.isNotBlank() }
        if (message != null) {
            AlertBanner(message, "danger")
        } else {
            Div({ classes("sql-result-placeholder") }) {
                Text("Ожидается завершение запроса.")
            }
        }
        return
    }

    Div({ classes("text-secondary", "small", "mb-3") }) {
        Text(
            buildString {
                append("Тип команды: ")
                append(result.statementKeyword)
                append(" • shard/source: ")
                append(result.shardResults.size)
                append(" • startedAt: ")
                append(execution.startedAt)
                if (!execution.finishedAt.isNullOrBlank()) {
                    append(" • finishedAt: ")
                    append(execution.finishedAt.orEmpty())
                }
            },
        )
    }
    if (result.shardResults.isEmpty()) {
        EmptyStateCard(
            title = "Результаты",
            text = "Сервер не вернул результатов по выбранным shard/source.",
        )
        return
    }
    Div({ classes("table-responsive", "mb-3") }) {
        Table({ classes("table", "table-striped", "table-hover", "align-middle", "mb-0") }) {
            Thead {
                Tr {
                    Th { Text("Source") }
                    Th { Text("Статус") }
                    Th { Text("Старт") }
                    Th { Text("Финиш") }
                    Th { Text("Длительность") }
                    Th { Text("Затронуто строк") }
                    Th { Text("Сообщение") }
                    Th { Text("Ошибка") }
                }
            }
            Tbody {
                result.shardResults.forEach { shard ->
                    Tr {
                        Td { org.jetbrains.compose.web.dom.B { Text(shard.shardName) } }
                        Td { StatusBadge(shard.status) }
                        Td { Text(formatDateTime(shard.startedAt)) }
                        Td { Text(formatDateTime(shard.finishedAt)) }
                        Td { Text(formatDuration(shard.startedAt, shard.finishedAt, running = shard.status.equals("RUNNING", ignoreCase = true))) }
                        Td { Text(shard.affectedRows?.toString() ?: "-") }
                        Td { Text(shard.message ?: "-") }
                        Td { Text(shard.errorMessage ?: "-") }
                    }
                }
            }
        }
    }
    Div({ classes("sql-shard-card-grid") }) {
        result.shardResults.forEach { shard ->
            Div({ classes("sql-shard-card", "status-${statusCssSuffix(shard.status)}") }) {
                Div({ classes("d-flex", "justify-content-between", "align-items-start", "gap-3") }) {
                    Div {
                        H3({ classes("h6", "mb-1") }) { Text(shard.shardName) }
                        Div({ classes("small", "text-secondary") }) {
                            Text(
                                buildString {
                                    append("Статус: ")
                                    append(shard.status)
                                    if (shard.affectedRows != null) {
                                        append(" • affectedRows: ")
                                        append(shard.affectedRows)
                                    }
                                    if (shard.rowCount > 0) {
                                        append(" • rows: ")
                                        append(shard.rowCount)
                                    }
                                    if (shard.durationMillis != null) {
                                        append(" • длительность: ")
                                        append(formatDurationMillis(shard.durationMillis))
                                    } else if (!shard.startedAt.isNullOrBlank()) {
                                        append(" • старт: ")
                                        append(formatDateTime(shard.startedAt))
                                    }
                                    if (shard.truncated) {
                                        append(" • результат усечен")
                                    }
                                },
                            )
                        }
                    }
                    StatusBadge(shard.status)
                }
                if (!shard.errorMessage.isNullOrBlank()) {
                    AlertBanner(shard.errorMessage ?: "", "danger")
                } else if (!shard.message.isNullOrBlank()) {
                    Div({ classes("alert", "alert-secondary", "mt-3", "mb-0") }) {
                        Text(shard.message ?: "")
                    }
                }
                Div({ classes("sql-shard-card-timings") }) {
                    Div { Text("Старт: ${formatDateTime(shard.startedAt)}") }
                    Div { Text("Финиш: ${formatDateTime(shard.finishedAt)}") }
                    Div {
                        Text(
                            "Длительность: ${
                                if (shard.durationMillis != null) {
                                    formatDurationMillis(shard.durationMillis)
                                } else {
                                    formatDuration(
                                        shard.startedAt,
                                        shard.finishedAt,
                                        running = shard.status.equals("RUNNING", ignoreCase = true),
                                    )
                                }
                            }",
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatusBadge(status: String) {
    val cssClass = "status-badge status-${statusCssSuffix(status)}"
    Span({ classes(*cssClass.split(" ").toTypedArray()) }) {
        Text(translateSourceStatus(status))
    }
}

internal fun buildConsoleInfoText(info: SqlConsoleInfo?): String =
    when {
        info == null -> "Конфигурация не загружена."
        !info.configured -> "SQL-консоль не настроена. Проверь конфигурацию источников и credential.properties."
        else -> "Доступно источников: ${info.sourceNames.size}. Лимит строк по умолчанию: ${info.maxRowsPerShard}."
    }

internal fun buildConnectionCheckStatusText(result: SqlConsoleConnectionCheckResponse): String {
    val success = result.sourceResults.count { it.status.equals("SUCCESS", ignoreCase = true) || it.status.equals("OK", ignoreCase = true) }
    val failed = result.sourceResults.size - success
    return "Проверка подключений завершена. Успешно: $success, с ошибкой: $failed."
}

internal fun sourceStatusCardClass(status: SqlConsoleSourceConnectionStatus?): String =
    "sql-source-checkbox-${sourceStatusTone(status)}"

internal fun Boolean?.orFalse(): Boolean = this == true

internal fun buildRunButtonClass(
    analysis: SqlStatementAnalysis,
    strictSafetyEnabled: Boolean,
): String = "btn-${runButtonTone(analysis, strictSafetyEnabled)}"

internal fun statusCssSuffix(status: String): String =
    sourceStatusSuffix(status)
