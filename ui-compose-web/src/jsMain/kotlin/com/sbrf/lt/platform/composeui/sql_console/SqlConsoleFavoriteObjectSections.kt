package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

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
    Div({ classes("sql-tool-window", "sql-tool-window-compact", "mb-3") }) {
        Div({ classes("sql-tool-window-head", "sql-tool-window-head-compact") }) {
            Div({ classes("sql-tool-window-heading") }) {
                Div({ classes("eyebrow", "mb-1") }) { Text("Tool Window") }
                Div({ classes("panel-title", "mb-0") }) { Text("Избранные объекты") }
            }
            Div({ classes("small", "text-secondary", "sql-tool-window-note") }) {
                Text("Сначала выбери SQL-действие, затем при необходимости открой инспектор или выполни вспомогательные действия.")
            }
        }
        Div({ classes("sql-favorite-objects-grid") }) {
            favorites.forEach { favorite ->
                val supportsPreview = supportsFavoriteRowPreview(favorite)
                Div({ classes("sql-favorite-object-card") }) {
                    Div({ classes("sql-favorite-object-meta") }) {
                        Div({ classes("sql-favorite-object-name") }) {
                            Text(favorite.qualifiedName())
                        }
                        Div({ classes("sql-favorite-object-note") }) {
                            Text(favorite.contextLabel())
                        }
                    }
                    Div({ classes("sql-favorite-object-actions") }) {
                        SqlLibraryActionButton(
                            primaryFavoriteActionLabel(supportsPreview),
                            "btn-dark",
                        ) {
                            onInsertSelect(favorite)
                        }
                        Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                            SqlLibraryActionButton(secondaryFavoriteActionLabel(supportsPreview), "btn-outline-dark") {
                                onInsert(favorite)
                            }
                            if (supportsPreview) {
                                SqlLibraryActionButton("COUNT(*)", "btn-outline-secondary") { onInsertCount(favorite) }
                            }
                        }
                        Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                            SqlLibraryActionButton("Инспектор", "btn-outline-secondary") { onOpenMetadata(favorite) }
                            SqlLibraryActionButton("Убрать", "btn-outline-danger") { onRemove(favorite) }
                        }
                    }
                }
            }
        }
    }
}

private fun primaryFavoriteActionLabel(supportsPreview: Boolean): String =
    if (supportsPreview) "Открыть SELECT" else "Открыть SQL"

private fun secondaryFavoriteActionLabel(supportsPreview: Boolean): String =
    if (supportsPreview) "Вставить имя" else "Вставить объект"
