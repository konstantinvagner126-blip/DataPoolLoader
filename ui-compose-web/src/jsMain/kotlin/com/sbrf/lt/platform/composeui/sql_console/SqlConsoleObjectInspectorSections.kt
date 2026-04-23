package com.sbrf.lt.platform.composeui.sql_console

import androidx.compose.runtime.Composable
import com.sbrf.lt.platform.composeui.foundation.dom.classes
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Pre
import org.jetbrains.compose.web.dom.Table
import org.jetbrains.compose.web.dom.Tbody
import org.jetbrains.compose.web.dom.Td
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Th
import org.jetbrains.compose.web.dom.Thead
import org.jetbrains.compose.web.dom.Tr

internal data class SqlObjectInspectorSelection(
    val sourceName: String,
    val dbObject: SqlConsoleDatabaseObject,
)

internal fun findSelectedObjectInSearchResponse(
    searchResponse: SqlConsoleObjectSearchResponse?,
    navigationTarget: SqlObjectNavigationTarget?,
): SqlObjectInspectorSelection? {
    if (searchResponse == null || navigationTarget == null) {
        return null
    }
    searchResponse.sourceResults.forEach { sourceResult ->
        val matchedObject = sourceResult.objects.firstOrNull { dbObject ->
            navigationTarget.matches(sourceResult.sourceName, dbObject)
        } ?: return@forEach
        return SqlObjectInspectorSelection(sourceResult.sourceName, matchedObject)
    }
    return null
}

internal fun directInspectorSelection(
    navigationTarget: SqlObjectNavigationTarget?,
): SqlObjectInspectorSelection? {
    if (navigationTarget == null) {
        return null
    }
    if (
        navigationTarget.sourceName.isBlank() ||
        navigationTarget.schemaName.isBlank() ||
        navigationTarget.objectName.isBlank() ||
        navigationTarget.objectType.isBlank()
    ) {
        return null
    }
    return SqlObjectInspectorSelection(
        sourceName = navigationTarget.sourceName,
        dbObject = SqlConsoleDatabaseObject(
            schemaName = navigationTarget.schemaName,
            objectName = navigationTarget.objectName,
            objectType = navigationTarget.objectType,
        ),
    )
}

internal fun resolveInspectorSelection(
    searchResponse: SqlConsoleObjectSearchResponse?,
    navigationTarget: SqlObjectNavigationTarget?,
): SqlObjectInspectorSelection? =
    directInspectorSelection(navigationTarget)
        ?: findSelectedObjectInSearchResponse(searchResponse, navigationTarget)

internal fun inspectorMatchesSelection(
    response: SqlConsoleObjectInspectorResponse?,
    selection: SqlObjectInspectorSelection?,
): Boolean {
    if (response == null || selection == null) {
        return false
    }
    val responseObject = response.dbObject
    val selectedObject = selection.dbObject
    return response.sourceName == selection.sourceName &&
        responseObject.schemaName == selectedObject.schemaName &&
        responseObject.objectName == selectedObject.objectName &&
        responseObject.objectType.equals(selectedObject.objectType, ignoreCase = true)
}

internal fun defaultInspectorTab(response: SqlConsoleObjectInspectorResponse): String =
    when {
        response.columns.isNotEmpty() -> "columns"
        response.schema != null -> "schema"
        response.sequence != null -> "sequence"
        response.trigger != null -> "trigger"
        response.indexes.isNotEmpty() -> "indexes"
        response.constraints.isNotEmpty() -> "constraints"
        !response.definition.isNullOrBlank() -> "ddl"
        else -> "overview"
    }

internal fun resolveInspectorTab(
    response: SqlConsoleObjectInspectorResponse,
    requestedTab: String?,
): String {
    val normalizedRequestedTab = requestedTab?.trim()?.lowercase()
    return normalizedRequestedTab
        ?.takeIf { it in inspectorTabIds(response).toSet() }
        ?: defaultInspectorTab(response)
}

internal fun inspectorTabIds(response: SqlConsoleObjectInspectorResponse): List<String> = buildList {
    add("overview")
    if (response.columns.isNotEmpty()) add("columns")
    if (response.indexes.isNotEmpty()) add("indexes")
    if (response.constraints.isNotEmpty()) add("constraints")
    if (response.relatedTriggers.isNotEmpty()) add("triggers")
    if (response.trigger != null) add("trigger")
    if (response.sequence != null) add("sequence")
    if (response.schema != null) add("schema")
    if (!response.definition.isNullOrBlank()) add("ddl")
}

@Composable
internal fun SqlObjectInspectorPanel(
    response: SqlConsoleObjectInspectorResponse,
    activeTab: String,
    onTabSelect: (String) -> Unit,
    onOpenSelect: () -> Unit,
    onOpenCount: () -> Unit,
) {
    val dbObject = response.dbObject
    SqlObjectPanel(
        title = "Инспектор объекта",
        note = "Type-aware tabbed inspector. Search results остаются легкими, а metadata загружается отдельно по выбранному объекту.",
        panelClasses = "sql-object-target-card mb-4",
        useParagraphNote = true,
    ) {
        Div({ classes("d-flex", "justify-content-between", "align-items-start", "gap-3", "mb-3") }) {
            SqlObjectIdentityBlock(
                name = dbObject.qualifiedName(),
                note = dbObject.contextLabel(response.sourceName),
                selectedNote = "Текущий объект инспектора",
                detailNote = dbObject.tableReferenceLabel(),
                nameClass = "sql-object-target-name",
            )
            if (supportsRowPreview(dbObject)) {
                Div({ classes("sql-object-action-row", "mb-0") }) {
                    SqlObjectActionButton("Открыть SELECT в консоли", "btn-dark") { onOpenSelect() }
                    SqlObjectActionButton("COUNT(*)", "btn-outline-dark") { onOpenCount() }
                }
            }
        }

        SqlObjectInspectorTabs(
            tabIds = inspectorTabIds(response),
            activeTab = activeTab,
            onTabSelect = onTabSelect,
        )

        when (activeTab) {
            "columns" -> SqlObjectColumnsTab(response.columns)
            "indexes" -> SqlObjectIndexesTab(response.indexes)
            "constraints" -> SqlObjectConstraintsTab(response.constraints)
            "triggers" -> SqlObjectRelatedTriggersTab(response.relatedTriggers)
            "trigger" -> SqlObjectTriggerTab(response.trigger)
            "sequence" -> SqlObjectSequenceTab(response.sequence)
            "schema" -> SqlObjectSchemaTab(response.schema)
            "ddl" -> SqlObjectDdlTab(response.definition)
            else -> SqlObjectOverviewTab(response)
        }
    }
}

@Composable
private fun SqlObjectInspectorTabs(
    tabIds: List<String>,
    activeTab: String,
    onTabSelect: (String) -> Unit,
) {
    Div({ classes("sql-object-inspector-tabs") }) {
        tabIds.forEach { tabId ->
            Button(attrs = {
                classes("sql-object-inspector-tab")
                if (tabId == activeTab) {
                    classes("sql-object-inspector-tab-active")
                }
                attr("type", "button")
                onClick { onTabSelect(tabId) }
            }) {
                Text(
                    when (tabId) {
                        "columns" -> "Колонки"
                        "indexes" -> "Индексы"
                        "constraints" -> "Констрейнты"
                        "triggers" -> "Триггеры"
                        "trigger" -> "Триггер"
                        "sequence" -> "Последовательность"
                        "schema" -> "Схема"
                        "ddl" -> "DDL"
                        else -> "Обзор"
                    },
                )
            }
        }
    }
}

@Composable
private fun SqlObjectOverviewTab(response: SqlConsoleObjectInspectorResponse) {
    val dbObject = response.dbObject
    Div({ classes("sql-object-inspector-panel") }) {
        Table({ classes("table", "table-sm", "sql-object-inspector-table") }) {
            Tbody {
                SqlObjectOverviewRow("Source", response.sourceName)
                SqlObjectOverviewRow("Тип", translateSqlObjectType(dbObject.objectType))
                SqlObjectOverviewRow("Схема", dbObject.schemaName.ifBlank { "-" })
                SqlObjectOverviewRow("Имя объекта", dbObject.objectName)
                val relatedTableName = dbObject.tableName
                if (!relatedTableName.isNullOrBlank()) {
                    SqlObjectOverviewRow("Связанный объект", relatedTableName)
                }
                SqlObjectOverviewRow("Колонок", response.columns.size.toString())
                SqlObjectOverviewRow("Индексов", response.indexes.size.toString())
                SqlObjectOverviewRow("Констрейнтов", response.constraints.size.toString())
                SqlObjectOverviewRow("DDL доступен", if (response.definition.isNullOrBlank()) "Нет" else "Да")
            }
        }
    }
}

@Composable
private fun SqlObjectOverviewRow(
    label: String,
    value: String,
) {
    Tr {
        Th { Text(label) }
        Td { Text(value) }
    }
}

@Composable
private fun SqlObjectColumnsTab(columns: List<SqlConsoleDatabaseObjectColumn>) {
    if (columns.isEmpty()) {
        SqlObjectInspectorEmptyState("Колонки для этого объекта недоступны.")
        return
    }
    Div({ classes("sql-object-inspector-panel") }) {
        Table({ classes("table", "table-sm", "sql-object-columns-table", "mb-0") }) {
            Thead {
                Tr {
                    Th { Text("Колонка") }
                    Th { Text("Тип") }
                    Th { Text("NULL") }
                }
            }
            Tbody {
                columns.forEach { column ->
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

@Composable
private fun SqlObjectIndexesTab(indexes: List<SqlConsoleDatabaseObjectIndex>) {
    if (indexes.isEmpty()) {
        SqlObjectInspectorEmptyState("Индексная metadata для этого объекта недоступна.")
        return
    }
    Div({ classes("sql-object-inspector-panel") }) {
        Table({ classes("table", "table-sm", "mb-0") }) {
            Thead {
                Tr {
                    Th { Text("Индекс") }
                    Th { Text("Колонки") }
                    Th { Text("Свойства") }
                }
            }
            Tbody {
                indexes.forEach { index ->
                    Tr {
                        Td { Text(index.name) }
                        Td { Text(index.columns.joinToString(", ").ifBlank { "-" }) }
                        Td {
                            Text(
                                buildList {
                                    if (index.primary == true) add("PRIMARY")
                                    if (index.unique == true) add("UNIQUE")
                                }.joinToString(", ").ifBlank { "-" },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SqlObjectConstraintsTab(constraints: List<SqlConsoleDatabaseObjectConstraint>) {
    if (constraints.isEmpty()) {
        SqlObjectInspectorEmptyState("Constraint metadata для этого объекта недоступна.")
        return
    }
    Div({ classes("sql-object-inspector-panel") }) {
        Table({ classes("table", "table-sm", "mb-0") }) {
            Thead {
                Tr {
                    Th { Text("Имя") }
                    Th { Text("Тип") }
                    Th { Text("Колонки") }
                }
            }
            Tbody {
                constraints.forEach { constraint ->
                    Tr {
                        Td { Text(constraint.name) }
                        Td { Text(constraint.type) }
                        Td { Text(constraint.columns.joinToString(", ").ifBlank { "-" }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SqlObjectRelatedTriggersTab(triggers: List<SqlConsoleDatabaseObjectTrigger>) {
    if (triggers.isEmpty()) {
        SqlObjectInspectorEmptyState("Связанных trigger-объектов не найдено.")
        return
    }
    Div({ classes("sql-object-inspector-panel") }) {
        Table({ classes("table", "table-sm", "mb-0") }) {
            Thead {
                Tr {
                    Th { Text("Триггер") }
                    Th { Text("Timing") }
                    Th { Text("Events") }
                    Th { Text("Функция") }
                }
            }
            Tbody {
                triggers.forEach { trigger ->
                    Tr {
                        Td { Text(trigger.name) }
                        Td { Text(trigger.timing ?: "-") }
                        Td { Text(trigger.events.joinToString(", ").ifBlank { "-" }) }
                        Td { Text(trigger.functionName ?: "-") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SqlObjectTriggerTab(trigger: SqlConsoleDatabaseObjectTrigger?) {
    if (trigger == null) {
        SqlObjectInspectorEmptyState("Trigger metadata недоступна.")
        return
    }
    Div({ classes("sql-object-inspector-panel") }) {
        Table({ classes("table", "table-sm", "sql-object-inspector-table", "mb-3") }) {
            Tbody {
                SqlObjectOverviewRow("Имя", trigger.name)
                SqlObjectOverviewRow("Target", trigger.targetObjectName ?: "-")
                SqlObjectOverviewRow("Timing", trigger.timing ?: "-")
                SqlObjectOverviewRow("Events", trigger.events.joinToString(", ").ifBlank { "-" })
                SqlObjectOverviewRow("Enabled", when (trigger.enabled) {
                    true -> "Да"
                    false -> "Нет"
                    null -> "-"
                })
                SqlObjectOverviewRow("Функция", trigger.functionName ?: "-")
            }
        }
        val triggerDefinition = trigger.definition
        if (!triggerDefinition.isNullOrBlank()) {
            Pre({ classes("sql-object-definition", "mb-0") }) {
                Text(triggerDefinition)
            }
        }
    }
}

@Composable
private fun SqlObjectSequenceTab(sequence: SqlConsoleDatabaseObjectSequence?) {
    if (sequence == null) {
        SqlObjectInspectorEmptyState("Sequence metadata недоступна.")
        return
    }
    Div({ classes("sql-object-inspector-panel") }) {
        Table({ classes("table", "table-sm", "sql-object-inspector-table", "mb-0") }) {
            Tbody {
                SqlObjectOverviewRow("Increment", sequence.incrementBy ?: "-")
                SqlObjectOverviewRow("Min", sequence.minimumValue ?: "-")
                SqlObjectOverviewRow("Max", sequence.maximumValue ?: "-")
                SqlObjectOverviewRow("Start", sequence.startValue ?: "-")
                SqlObjectOverviewRow("Cache", sequence.cacheSize ?: "-")
                SqlObjectOverviewRow("Cycle", when (sequence.cycle) {
                    true -> "Да"
                    false -> "Нет"
                    null -> "-"
                })
                SqlObjectOverviewRow("Owned by", sequence.ownedBy ?: "-")
            }
        }
    }
}

@Composable
private fun SqlObjectSchemaTab(schema: SqlConsoleDatabaseObjectSchema?) {
    if (schema == null) {
        SqlObjectInspectorEmptyState("Schema metadata недоступна.")
        return
    }
    Div({ classes("sql-object-inspector-panel") }) {
        Table({ classes("table", "table-sm", "sql-object-inspector-table", "mb-3") }) {
            Tbody {
                SqlObjectOverviewRow("Owner", schema.owner ?: "-")
                SqlObjectOverviewRow("Comment", schema.comment ?: "-")
            }
        }
        if (schema.objectCounts.isNotEmpty()) {
            Table({ classes("table", "table-sm", "mb-3") }) {
                Thead {
                    Tr {
                        Th { Text("Тип") }
                        Th { Text("Количество") }
                    }
                }
                Tbody {
                    schema.objectCounts.forEach { item ->
                        Tr {
                            Td { Text(item.label) }
                            Td { Text(item.count.toString()) }
                        }
                    }
                }
            }
        }
        if (schema.privileges.isNotEmpty()) {
            Div({ classes("sql-object-index-list") }) {
                schema.privileges.forEach { privilege ->
                    Div({ classes("sql-object-index-chip") }) {
                        Text(privilege)
                    }
                }
            }
        }
    }
}

@Composable
private fun SqlObjectDdlTab(definition: String?) {
    val ddl = definition
    if (ddl.isNullOrBlank()) {
        SqlObjectInspectorEmptyState("DDL для этого объекта пока недоступен.")
        return
    }
    Div({ classes("sql-object-inspector-panel") }) {
        Pre({ classes("sql-object-definition", "mb-0") }) {
            Text(ddl)
        }
    }
}

@Composable
private fun SqlObjectInspectorEmptyState(text: String) {
    Div({ classes("sql-object-inspector-empty") }) {
        Text(text)
    }
}
