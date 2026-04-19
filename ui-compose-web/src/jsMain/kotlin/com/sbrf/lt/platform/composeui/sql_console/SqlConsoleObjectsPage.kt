package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.sbrf.lt.platform.composeui.foundation.component.AlertBanner
import com.sbrf.lt.platform.composeui.foundation.component.EmptyStateCard
import com.sbrf.lt.platform.composeui.foundation.component.LoadingStateCard
import com.sbrf.lt.platform.composeui.foundation.component.PageScaffold
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import com.sbrf.lt.platform.composeui.foundation.dom.classesFromString
import com.sbrf.lt.platform.composeui.foundation.runtime.buildRuntimeModeFallbackMessage
import com.sbrf.lt.platform.composeui.foundation.runtime.hasModeFallback
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.href
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.value
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H4
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
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
fun ComposeSqlConsoleObjectsPage(
    initialParams: Map<String, String> = emptyMap(),
    api: SqlConsoleApi = remember { SqlConsoleApiClient() },
) {
    val store = remember(api) { SqlConsoleObjectsStore(api) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(SqlConsoleObjectsPageState()) }
    val initialQuery = initialParams["query"]?.trim().orEmpty()
        .ifBlank { initialParams["object"]?.trim().orEmpty() }
    val initialSource = initialParams["source"]?.trim().orEmpty()
    val navigationTarget = remember(initialParams) {
        initialParams["object"]?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { objectName ->
                SqlObjectNavigationTarget(
                    sourceName = initialParams["source"]?.trim().orEmpty(),
                    schemaName = initialParams["schema"]?.trim().orEmpty(),
                    objectName = objectName,
                    objectType = initialParams["type"]?.trim().orEmpty(),
                )
            }
    }
    val runtimeContext = state.runtimeContext
    val searchResponse = state.searchResponse
    val totalFoundObjects = searchResponse?.sourceResults?.sumOf { it.objects.size } ?: 0

    LaunchedEffect(store, initialQuery, initialSource) {
        state = store.startLoading(state)
        var nextState = store.load()
        if (initialQuery.isNotBlank()) {
            nextState = store.updateQuery(nextState, initialQuery)
        }
        if (initialSource.isNotBlank() && initialSource in (nextState.info?.sourceNames ?: emptyList())) {
            nextState = nextState.copy(selectedSourceNames = listOf(initialSource))
        }
        state = nextState
        if (initialQuery.length >= 2) {
            state = store.beginAction(state, "search")
            state = store.search(state)
        }
    }

    PageScaffold(
        eyebrow = "MLP Platform",
        title = "Объекты БД",
        subtitle = "Search-first просмотр таблиц, индексов и представлений по источникам SQL-консоли без полной загрузки большого каталога.",
        heroClassNames = listOf("hero-card-compact", "sql-console-hero"),
        heroCopyClassNames = listOf("sql-console-hero-copy"),
        heroHeader = {
            Div({ classes("hero-actions", "mb-3") }) {
                ObjectsNavActionButton("На главную", hrefValue = "/")
                ObjectsNavActionButton("SQL-консоль", hrefValue = "/sql-console")
                ObjectsNavActionButton("Объекты БД", active = true)
            }
        },
        content = {
            state.errorMessage?.let { AlertBanner(it, "warning") }
            state.successMessage?.let { AlertBanner(it, "success") }
            runtimeContext?.takeIf { it.hasModeFallback() }?.let { fallbackContext ->
                AlertBanner(
                    buildRuntimeModeFallbackMessage(fallbackContext),
                    "warning",
                )
            }

            if (state.loading && state.info == null) {
                LoadingStateCard(
                    title = "Объекты БД",
                    text = "Конфигурация SQL-консоли загружается.",
                )
                return@PageScaffold
            }

            Div({ classes("sql-object-overview-grid") }) {
                SqlObjectOverviewCard("Источники", state.selectedSourceNames.size.toString(), "Выбрано для поиска")
                SqlObjectOverviewCard("Найдено", totalFoundObjects.toString(), "Объектов в последнем результате")
                SqlObjectOverviewCard(
                    "Лимит",
                    (searchResponse?.maxObjectsPerSource ?: 30).toString(),
                    "Максимум результатов на source",
                )
            }

            navigationTarget?.let { target ->
                SqlObjectPanel(title = "Текущий объект", panelClasses = "sql-object-target-card mb-4") {
                    SqlObjectIdentityBlock(
                        name = target.qualifiedName(),
                        note = target.contextLabel(),
                        nameClass = "sql-object-target-name",
                    )
                }
            }

            if (state.favoriteObjects.isNotEmpty()) {
                SqlObjectPanel(
                    title = "Избранные объекты",
                    note = "Эти объекты доступны для быстрой вставки в основной SQL-редактор.",
                    panelClasses = "panel mb-4",
                ) {
                    Div({ classes("sql-favorite-objects-grid") }) {
                        state.favoriteObjects.forEach { favorite ->
                            Div({ classes("sql-favorite-object-card") }) {
                                SqlObjectIdentityBlock(
                                    name = favorite.qualifiedName(),
                                    note = favorite.contextLabel(),
                                    wrapperClass = "sql-favorite-object-meta",
                                    nameClass = "sql-favorite-object-name",
                                    noteClass = "sql-favorite-object-note",
                                )
                            }
                        }
                    }
                }
            }

            Div({ classes("row", "g-4") }) {
                Div({ classes("col-12", "col-xl-3") }) {
                    SqlObjectPanel(
                        title = "Sources",
                        note = "Выбери, по каким источникам искать объекты БД.",
                        panelClasses = "panel h-100",
                        useParagraphNote = true,
                    ) {
                        state.info?.sourceNames?.forEach { sourceName ->
                            val selected = sourceName in state.selectedSourceNames
                            Label(attrs = {
                                classes("sql-object-source-checkbox")
                                if (selected) {
                                    classes("sql-object-source-checkbox-selected")
                                }
                            }) {
                                Input(type = org.jetbrains.compose.web.attributes.InputType.Checkbox, attrs = {
                                    if (selected) {
                                        attr("checked", "checked")
                                    }
                                    onClick {
                                        state = store.updateSelectedSources(state, sourceName, !selected)
                                    }
                                })
                                Span { Text(sourceName) }
                            }
                        }
                    }
                }
                Div({ classes("col-12", "col-xl-9") }) {
                    SqlObjectPanel(
                        title = "Поиск объектов",
                        note = "Введи имя объекта или часть имени. Полный каталог схемы на старте не загружается.",
                        panelClasses = "panel mb-4",
                        useParagraphNote = true,
                    ) {
                        Div({ classes("sql-object-search-toolbar") }) {
                            Input(type = org.jetbrains.compose.web.attributes.InputType.Text, attrs = {
                                classes("form-control", "sql-object-search-input")
                                placeholder("Например: customer, offer, idx_offer_status")
                                value(state.query)
                                onInput { state = store.updateQuery(state, it.value?.toString().orEmpty()) }
                            })
                            Button(attrs = {
                                classes("btn", "btn-dark")
                                attr("type", "button")
                                if (state.actionInProgress == "search" || state.selectedSourceNames.isEmpty()) {
                                    disabled()
                                }
                                onClick {
                                    scope.launch {
                                        state = store.beginAction(state, "search")
                                        state = store.search(state)
                                    }
                                }
                            }) {
                                Text(if (state.actionInProgress == "search") "Поиск..." else "Искать")
                            }
                        }
                    }

                    when {
                        searchResponse == null -> {
                            EmptyStateCard(
                                title = "Поиск еще не выполнялся",
                                text = "Введи поисковый запрос и запусти поиск по выбранным источникам.",
                            )
                        }

                        searchResponse.sourceResults.all { it.objects.isEmpty() && it.errorMessage == null } -> {
                            EmptyStateCard(
                                title = "Ничего не найдено",
                                text = "Попробуй уточнить имя объекта или выбрать другие источники.",
                            )
                        }

                        else -> {
                            Div({ classes("sql-object-source-results") }) {
                                searchResponse.sourceResults.forEach { sourceResult ->
                                    Div({ classes("panel", "sql-object-source-panel") }) {
                                        Div({ classes("d-flex", "justify-content-between", "align-items-center", "gap-3", "mb-3") }) {
                                            H4({ classes("panel-title", "mb-0") }) {
                                                Text(sourceResult.sourceName)
                                            }
                                            Span({ classes("sql-object-source-summary") }) {
                                                Text("Найдено: ${sourceResult.objects.size}")
                                            }
                                        }
                                        sourceResult.errorMessage?.let { errorMessage ->
                                            AlertBanner(errorMessage, "warning")
                                        } ?: if (sourceResult.objects.isEmpty()) {
                                            Div({ classes("small", "text-secondary") }) {
                                                Text("По этому source совпадений нет.")
                                            }
                                        } else {
                                            if (sourceResult.truncated) {
                                                Div({ classes("small", "text-secondary", "mb-3") }) {
                                                    Text("Показаны первые ${searchResponse.maxObjectsPerSource} объектов по source.")
                                                }
                                            }
                                            Div({ classes("sql-object-result-list") }) {
                                                sourceResult.objects
                                                    .sortedByDescending { dbObject ->
                                                        navigationTarget?.matches(sourceResult.sourceName, dbObject) == true
                                                    }
                                                    .forEach { dbObject ->
                                                    val isSelectedObject = navigationTarget?.matches(sourceResult.sourceName, dbObject) == true
                                                    SqlConsoleObjectCard(
                                                        sourceName = sourceResult.sourceName,
                                                        dbObject = dbObject,
                                                        isSelectedObject = isSelectedObject,
                                                        isFavorite = state.favoriteObjects.any {
                                                            it.sourceName == sourceResult.sourceName &&
                                                                it.schemaName == dbObject.schemaName &&
                                                                it.objectName == dbObject.objectName &&
                                                                it.objectType == dbObject.objectType &&
                                                                it.tableName == dbObject.tableName
                                                        },
                                                        onToggleFavorite = {
                                                            scope.launch {
                                                                state = store.beginAction(state, "toggle-favorite-object")
                                                                state = store.toggleFavoriteObject(state, sourceResult.sourceName, dbObject)
                                                            }
                                                        },
                                                        onOpenSelect = {
                                                            scope.launch {
                                                                state = store.beginAction(state, "open-object-select")
                                                                state = store.openObjectInConsole(
                                                                    current = state,
                                                                    sourceName = sourceResult.sourceName,
                                                                    draftSql = buildPreviewSql(dbObject),
                                                                )
                                                                if (state.errorMessage == null) {
                                                                    window.location.href = "/sql-console"
                                                                }
                                                            }
                                                        },
                                                        onOpenCount = {
                                                            scope.launch {
                                                                state = store.beginAction(state, "open-object-count")
                                                                state = store.openObjectInConsole(
                                                                    current = state,
                                                                    sourceName = sourceResult.sourceName,
                                                                    draftSql = buildCountSql(dbObject),
                                                                )
                                                                if (state.errorMessage == null) {
                                                                    window.location.href = "/sql-console"
                                                                }
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
                    }
                }
            }
        },
    )
}

@Composable
private fun ObjectsNavActionButton(
    label: String,
    hrefValue: String? = null,
    active: Boolean = false,
) {
    if (active) {
        Button(attrs = {
            classes("btn", "btn-dark")
            attr("type", "button")
            disabled()
        }) { Text(label) }
        return
    }
    A(attrs = {
        classes("btn", "btn-outline-secondary")
        href(hrefValue ?: "#")
    }) { Text(label) }
}

@Composable
private fun SqlObjectPanel(
    title: String,
    note: String? = null,
    panelClasses: String = "panel",
    useParagraphNote: Boolean = false,
    content: @Composable () -> Unit,
) {
    Div({ classesFromString(panelClasses) }) {
        Div({ classes("panel-title", "mb-2") }) { Text(title) }
        if (!note.isNullOrBlank()) {
            if (useParagraphNote) {
                P({ classes("small", "text-secondary", "mb-3") }) {
                    Text(note)
                }
            } else {
                Div({ classes("small", "text-secondary", "mb-3") }) {
                    Text(note)
                }
            }
        }
        content()
    }
}

@Composable
private fun SqlObjectOverviewCard(
    label: String,
    value: String,
    note: String,
) {
    Div({ classes("sql-object-overview-card") }) {
        Div({ classes("eyebrow") }) { Text(label) }
        Div({ classes("sql-object-overview-value") }) { Text(value) }
        Div({ classes("small", "text-secondary") }) { Text(note) }
    }
}

@Composable
private fun SqlConsoleObjectCard(
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
private fun SqlObjectIdentityBlock(
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
