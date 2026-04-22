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
                            Text(favorite.contextLabel())
                        }
                    }
                    Div({ classes("d-flex", "flex-wrap", "gap-2") }) {
                        SqlLibraryActionButton("Вставить", "btn-outline-dark") { onInsert(favorite) }
                        SqlLibraryActionButton(
                            if (supportsFavoriteRowPreview(favorite)) "SELECT *" else "В SQL",
                            "btn-dark",
                        ) {
                            onInsertSelect(favorite)
                        }
                        if (supportsFavoriteRowPreview(favorite)) {
                            SqlLibraryActionButton("COUNT(*)", "btn-outline-dark") { onInsertCount(favorite) }
                        }
                        SqlLibraryActionButton("Метаданные", "btn-outline-secondary") { onOpenMetadata(favorite) }
                        SqlLibraryActionButton("Убрать", "btn-outline-danger") { onRemove(favorite) }
                    }
                }
            }
        }
    }
}
