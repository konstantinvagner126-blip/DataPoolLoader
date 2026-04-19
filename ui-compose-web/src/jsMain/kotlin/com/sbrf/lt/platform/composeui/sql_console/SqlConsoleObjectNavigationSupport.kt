package com.sbrf.lt.platform.composeui.sql_console

internal data class SqlObjectNavigationTarget(
    val sourceName: String,
    val schemaName: String,
    val objectName: String,
    val objectType: String,
)

internal fun SqlObjectNavigationTarget.qualifiedName(): String =
    buildString {
        if (schemaName.isNotBlank()) {
            append(schemaName)
            append(".")
        }
        append(objectName)
    }

internal fun SqlObjectNavigationTarget.contextLabel(): String =
    buildString {
        if (sourceName.isNotBlank()) {
            append(sourceName)
        }
        if (objectType.isNotBlank()) {
            if (isNotEmpty()) append(" • ")
            append(translateSqlObjectType(objectType))
        }
    }

internal fun SqlObjectNavigationTarget.matches(
    sourceName: String,
    dbObject: SqlConsoleDatabaseObject,
): Boolean {
    if (this.sourceName.isNotBlank() && this.sourceName != sourceName) {
        return false
    }
    if (schemaName.isNotBlank() && schemaName != dbObject.schemaName) {
        return false
    }
    if (objectName != dbObject.objectName) {
        return false
    }
    if (objectType.isNotBlank() && objectType.uppercase() != dbObject.objectType.uppercase()) {
        return false
    }
    return true
}
