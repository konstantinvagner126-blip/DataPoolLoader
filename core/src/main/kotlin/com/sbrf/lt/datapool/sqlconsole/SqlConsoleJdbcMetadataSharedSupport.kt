package com.sbrf.lt.datapool.sqlconsole

import java.sql.ResultSet

internal fun ResultSet.toIndexMetadata(): SqlConsoleDatabaseObjectIndex =
    SqlConsoleDatabaseObjectIndex(
        name = getString("indexname"),
        tableName = getString("tablename"),
        columns = getStringArray("column_names"),
        unique = getBoolean("indisunique"),
        primary = getBoolean("indisprimary"),
        definition = getString("indexdef"),
    )

internal fun ResultSet.toTriggerMetadata(): SqlConsoleDatabaseObjectTrigger {
    val triggerType = getInt("tgtype")
    return SqlConsoleDatabaseObjectTrigger(
        name = getString("tgname"),
        targetObjectName = getString("table_name"),
        timing = decodeTriggerTiming(triggerType),
        events = decodeTriggerEvents(triggerType),
        enabled = getString("tgenabled") != "D",
        functionName = getString("function_name"),
        definition = getString("definition"),
    )
}

internal fun ResultSet.getStringArray(columnLabel: String): List<String> {
    val sqlArray = getArray(columnLabel) ?: return emptyList()
    val raw = sqlArray.array
    return when (raw) {
        is Array<*> -> raw.filterIsInstance<String>()
        is Collection<*> -> raw.filterIsInstance<String>()
        else -> emptyList()
    }
}

internal fun translateConstraintType(typeCode: String): String =
    when (typeCode) {
        "p" -> "PRIMARY KEY"
        "u" -> "UNIQUE"
        "f" -> "FOREIGN KEY"
        "c" -> "CHECK"
        "x" -> "EXCLUDE"
        else -> typeCode
    }

internal fun decodeTriggerTiming(triggerType: Int): String =
    when {
        triggerType and 64 != 0 -> "INSTEAD OF"
        triggerType and 2 != 0 -> "BEFORE"
        else -> "AFTER"
    }

internal fun decodeTriggerEvents(triggerType: Int): List<String> = buildList {
    if (triggerType and 4 != 0) add("INSERT")
    if (triggerType and 8 != 0) add("DELETE")
    if (triggerType and 16 != 0) add("UPDATE")
    if (triggerType and 32 != 0) add("TRUNCATE")
}

internal fun buildSyntheticTableDefinition(
    schemaName: String,
    objectName: String,
    columns: List<SqlConsoleDatabaseObjectColumn>,
    constraints: List<SqlConsoleDatabaseObjectConstraint>,
): String =
    buildString {
        append("create table ")
        append(sqlQualifiedName(schemaName, objectName))
        append(" (\n")
        val columnDefinitions = columns.map { column ->
            buildString {
                append("    ")
                append(sqlIdentifier(column.name))
                append(" ")
                append(column.type)
                if (!column.nullable) {
                    append(" not null")
                }
            }
        }
        val constraintDefinitions = constraints.mapNotNull { constraint ->
            constraint.definition?.takeIf { it.isNotBlank() }?.let { "    constraint ${sqlIdentifier(constraint.name)} $it" }
        }
        append((columnDefinitions + constraintDefinitions).joinToString(",\n"))
        append("\n);")
    }

internal fun buildSequenceDefinition(
    schemaName: String,
    objectName: String,
    sequence: SqlConsoleDatabaseObjectSequence,
): String =
    buildString {
        append("create sequence ")
        append(sqlQualifiedName(schemaName, objectName))
        sequence.incrementBy?.let {
            append("\n    increment by ")
            append(it)
        }
        sequence.minimumValue?.let {
            append("\n    minvalue ")
            append(it)
        }
        sequence.maximumValue?.let {
            append("\n    maxvalue ")
            append(it)
        }
        sequence.startValue?.let {
            append("\n    start with ")
            append(it)
        }
        sequence.cacheSize?.let {
            append("\n    cache ")
            append(it)
        }
        if (sequence.cycle == true) {
            append("\n    cycle")
        }
        append(";")
    }

internal fun sqlIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""

internal fun sqlQualifiedName(
    schemaName: String,
    objectName: String,
): String = "${sqlIdentifier(schemaName)}.${sqlIdentifier(objectName)}"

internal fun objectIdentityKey(value: SqlConsoleDatabaseObject): String =
    listOf(value.schemaName, value.objectName, value.objectType.name, value.tableName.orEmpty()).joinToString("|")
