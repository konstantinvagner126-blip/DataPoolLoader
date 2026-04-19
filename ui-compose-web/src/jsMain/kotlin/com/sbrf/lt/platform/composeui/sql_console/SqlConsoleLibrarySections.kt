package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Option
import org.jetbrains.compose.web.dom.Select
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

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
