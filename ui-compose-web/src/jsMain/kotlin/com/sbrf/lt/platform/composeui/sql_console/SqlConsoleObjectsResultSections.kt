package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.dom.classesFromString
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

@Composable
internal fun SqlObjectSourceResultPanel(
    sourceName: String,
    objectCount: Int,
    content: @Composable () -> Unit,
) {
    Div({ classes("panel", "sql-object-source-panel") }) {
        Div({ classes("d-flex", "justify-content-between", "align-items-center", "gap-3", "mb-3") }) {
            H4({ classes("panel-title", "mb-0") }) {
                Text(sourceName)
            }
            Span({ classes("sql-object-source-summary") }) {
                Text("Найдено: $objectCount")
            }
        }
        content()
    }
}

@Composable
internal fun SqlObjectSourceMutedText(
    text: String,
    marginClass: String = "",
) {
    Div({
        classes("small", "text-secondary")
        if (marginClass.isNotBlank()) {
            classes(marginClass)
        }
    }) {
        Text(text)
    }
}

@Composable
internal fun SqlConsoleObjectCard(
    sourceName: String,
    dbObject: SqlConsoleDatabaseObject,
    isSelectedObject: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenSelect: () -> Unit,
    onOpenCount: () -> Unit,
) {
    Div({
        classes("sql-object-card")
        if (isSelectedObject) {
            classes("sql-object-card-selected")
        }
    }) {
        Div({ classes("d-flex", "justify-content-between", "align-items-start", "gap-3", "mb-2") }) {
            SqlObjectIdentityBlock(
                name = dbObject.qualifiedName(),
                note = dbObject.contextLabel(sourceName),
                selectedNote = if (isSelectedObject) "Точное совпадение по deep-link" else null,
                detailNote = dbObject.tableReferenceLabel(),
            )
            SqlObjectActionButton(
                if (isFavorite) "Убрать" else "В избранное",
                if (isFavorite) "btn-outline-danger" else "btn-outline-dark",
            ) { onToggleFavorite() }
        }

        Div({ classes("sql-object-action-row") }) {
            SqlObjectActionButton(
                if (supportsRowPreview(dbObject)) "SELECT *" else "В SQL",
                "btn-dark",
            ) { onOpenSelect() }
            if (supportsRowPreview(dbObject)) {
                SqlObjectActionButton("COUNT(*)", "btn-outline-dark") { onOpenCount() }
            }
        }

        if (dbObject.indexNames.isNotEmpty()) {
            Div({ classes("small", "text-secondary", "mb-3") }) {
                Text("Индексы: ${dbObject.indexNames.joinToString(", ")}")
            }
        }

        dbObject.definition?.let { definition ->
            Pre({ classes("sql-object-definition") }) {
                Text(definition)
            }
        }

        if (dbObject.columns.isNotEmpty()) {
            Table({ classes("table", "table-sm", "sql-object-columns-table") }) {
                Thead {
                    Tr {
                        Th { Text("Колонка") }
                        Th { Text("Тип") }
                        Th { Text("NULL") }
                    }
                }
                Tbody {
                    dbObject.columns.forEach { column ->
                        Tr {
                            Td { Text(column.name) }
                            Td { Text(column.type) }
                            Td { Text(if (column.nullable) "Да" else "Нет") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SqlObjectActionButton(
    label: String,
    toneClass: String,
    onClick: () -> Unit,
) {
    Button(attrs = {
        classes("btn", toneClass, "btn-sm")
        attr("type", "button")
        onClick { onClick() }
    }) {
        Text(label)
    }
}

@Composable
internal fun SqlObjectIdentityBlock(
    name: String,
    note: String,
    wrapperClass: String? = null,
    nameClass: String = "sql-object-name",
    noteClass: String = "small text-secondary",
    selectedNote: String? = null,
    detailNote: String? = null,
) {
    Div({
        wrapperClass?.let(::classesFromString)
    }) {
        Div({ classes(nameClass) }) {
            Text(name)
        }
        Div({ classesFromString(noteClass) }) {
            Text(note)
        }
        if (!selectedNote.isNullOrBlank()) {
            Div({ classes("sql-object-selected-note") }) {
                Text(selectedNote)
            }
        }
        if (!detailNote.isNullOrBlank()) {
            Div({ classes("small", "text-secondary") }) {
                Text(detailNote)
            }
        }
    }
}
