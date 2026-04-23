package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

@Composable
internal fun SqlConsoleShortcutPanel(
    editorFocused: Boolean,
    onFocusEditor: () -> Unit,
) {
    Div({ classes("sql-shortcut-panel") }) {
        Div({ classes("sql-shortcut-panel-head") }) {
            Div {
                Div({ classes("sql-shortcut-panel-title") }) { Text("Горячие клавиши Monaco") }
                Div({ classes("sql-shortcut-panel-note") }) {
                    Text(
                        if (editorFocused) {
                            "Курсор сейчас в редакторе. Команды работают editor-local и не лезут в глобальные browser shortcuts."
                        } else {
                            "Команды работают только когда фокус внутри Monaco. Верни фокус в редактор и повтори shortcut."
                        },
                    )
                }
            }
            Button(attrs = {
                classes("btn", if (editorFocused) "btn-dark" else "btn-outline-dark", "btn-sm")
                attr("type", "button")
                if (editorFocused) {
                    disabled()
                }
                onClick { onFocusEditor() }
            }) {
                Text(if (editorFocused) "Фокус в Monaco" else "Вернуть фокус в Monaco")
            }
        }
        Div({ classes("sql-shortcut-groups") }) {
            SqlShortcutGroup(
                title = "Выполнение",
                items = listOf(
                    "Ctrl/Cmd + Enter -> Выполнить скрипт",
                    "Ctrl/Cmd + Shift + Enter -> Выполнить текущий statement",
                    "Esc -> Остановить выполнение",
                    "Shift + Alt + F -> Форматировать SQL",
                ),
            )
            SqlShortcutGroup(
                title = "Результаты",
                items = listOf(
                    "Ctrl/Cmd + Alt + 1 -> Вкладка Данные",
                    "Ctrl/Cmd + Alt + 2 -> Вкладка Статусы",
                    "Ctrl/Cmd + Alt + ↑ / ↓ -> Предыдущий / следующий statement",
                    "Ctrl/Cmd + Alt + ← / → -> Предыдущий / следующий source",
                    "Ctrl/Cmd + Alt + PgUp / PgDn -> Предыдущая / следующая страница",
                ),
            )
        }
    }
}

@Composable
private fun SqlShortcutGroup(
    title: String,
    items: List<String>,
) {
    Div({ classes("sql-shortcut-group") }) {
        Div({ classes("sql-shortcut-group-title") }) { Text(title) }
        items.forEach { item ->
            Div({ classes("sql-shortcut-item") }) {
                Span({ classes("sql-shortcut-item-bullet") }) { Text("•") }
                Span { Text(item) }
            }
        }
    }
}
