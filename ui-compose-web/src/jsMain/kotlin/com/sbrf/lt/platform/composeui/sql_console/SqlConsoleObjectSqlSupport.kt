package com.sbrf.lt.platform.composeui.sql_console

internal data class SqlObjectNavigationTarget(
    val sourceName: String,
    val schemaName: String,
    val objectName: String,
    val objectType: String,
    val inspectorTab: String? = null,
)

internal fun SqlConsoleFavoriteObject.qualifiedName(): String =
    buildSqlObjectQualifiedName(schemaName, objectName)

internal fun SqlConsoleDatabaseObject.qualifiedName(): String =
    buildSqlObjectQualifiedName(schemaName, objectName)

internal fun SqlConsoleFavoriteObject.contextLabel(): String =
    buildSqlObjectContextLabel(sourceName, objectType)

internal fun SqlConsoleDatabaseObject.contextLabel(sourceName: String): String =
    buildSqlObjectContextLabel(sourceName, objectType)

internal fun SqlConsoleDatabaseObject.tableReferenceLabel(): String? =
    tableName?.let { "Таблица: ${schemaName}.$it" }

internal fun supportsFavoriteRowPreview(favorite: SqlConsoleFavoriteObject): Boolean =
    supportsRowPreview(favorite.objectType)

internal fun supportsRowPreview(dbObject: SqlConsoleDatabaseObject): Boolean =
    supportsRowPreview(dbObject.objectType)

internal fun supportsRowPreview(objectType: String): Boolean =
    when (objectType.uppercase()) {
        "TABLE", "VIEW", "MATERIALIZED_VIEW" -> true
        else -> false
    }

internal fun buildFavoritePreviewSql(favorite: SqlConsoleFavoriteObject): String =
    buildPreviewSql(
        schemaName = favorite.schemaName,
        objectName = favorite.objectName,
        objectType = favorite.objectType,
    )

internal fun buildFavoriteCountSql(favorite: SqlConsoleFavoriteObject): String =
    buildCountSql(
        schemaName = favorite.schemaName,
        objectName = favorite.objectName,
    )

internal fun buildPreviewSql(dbObject: SqlConsoleDatabaseObject): String =
    buildPreviewSql(
        schemaName = dbObject.schemaName,
        objectName = dbObject.objectName,
        objectType = dbObject.objectType,
    )

internal fun buildCountSql(dbObject: SqlConsoleDatabaseObject): String =
    buildCountSql(
        schemaName = dbObject.schemaName,
        objectName = dbObject.objectName,
    )

internal fun buildFavoriteMetadataHref(
    favorite: SqlConsoleFavoriteObject,
    workspaceId: String = resolveSqlConsoleWorkspaceId(),
    inspectorTab: String? = null,
): String =
    buildSqlConsoleObjectsHref(
        workspaceId = workspaceId,
        query = favorite.objectName,
        sourceName = favorite.sourceName,
        schemaName = favorite.schemaName,
        objectName = favorite.objectName,
        objectType = favorite.objectType,
        inspectorTab = inspectorTab,
    )

internal fun buildObjectInspectorHref(
    sourceName: String,
    dbObject: SqlConsoleDatabaseObject,
    workspaceId: String = resolveSqlConsoleWorkspaceId(),
    inspectorTab: String? = null,
): String =
    buildSqlConsoleObjectsHref(
        workspaceId = workspaceId,
        query = dbObject.objectName,
        sourceName = sourceName,
        schemaName = dbObject.schemaName,
        objectName = dbObject.objectName,
        objectType = dbObject.objectType,
        inspectorTab = inspectorTab,
    )

internal fun translateSqlObjectType(type: String): String =
    when (type.uppercase()) {
        "TABLE" -> "Таблица"
        "VIEW" -> "Представление"
        "MATERIALIZED_VIEW" -> "Материализованное представление"
        "INDEX" -> "Индекс"
        "SEQUENCE" -> "Последовательность"
        "TRIGGER" -> "Триггер"
        "SCHEMA" -> "Схема"
        else -> type
    }

internal fun sqlQualifiedName(
    schemaName: String,
    objectName: String,
): String = "${sqlIdentifier(schemaName)}.${sqlIdentifier(objectName)}"

internal fun sqlIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""

internal fun sqlLiteral(value: String): String = "'${value.replace("'", "''")}'"

internal fun urlEncode(value: String): String = js("encodeURIComponent(value)") as String

internal fun buildSqlConsoleObjectsWorkspaceHref(workspaceId: String): String =
    buildSqlConsoleObjectsHref(workspaceId = workspaceId)

internal fun buildSqlObjectQualifiedName(
    schemaName: String,
    objectName: String,
): String =
    buildString {
        if (schemaName.isNotBlank()) {
            append(schemaName)
            append(".")
        }
        append(objectName)
    }

internal fun buildSqlObjectContextLabel(
    sourceName: String,
    objectType: String,
): String =
    buildString {
        if (sourceName.isNotBlank()) {
            append(sourceName)
        }
        if (objectType.isNotBlank()) {
            if (isNotEmpty()) append(" • ")
            append(translateSqlObjectType(objectType))
        }
    }

internal fun SqlObjectNavigationTarget.qualifiedName(): String =
    buildSqlObjectQualifiedName(schemaName, objectName)

internal fun SqlObjectNavigationTarget.contextLabel(): String =
    buildSqlObjectContextLabel(sourceName, objectType)

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

private fun buildPreviewSql(
    schemaName: String,
    objectName: String,
    objectType: String,
): String {
    val qualifiedName = sqlQualifiedName(schemaName, objectName)
    return if (supportsRowPreview(objectType)) {
        """
        select *
        from $qualifiedName
        limit 100;
        """.trimIndent()
    } else {
        """
        select schemaname,
               tablename,
               indexname,
               indexdef
        from pg_catalog.pg_indexes
        where schemaname = ${sqlLiteral(schemaName)}
          and indexname = ${sqlLiteral(objectName)};
        """.trimIndent()
    }
}

private fun buildCountSql(
    schemaName: String,
    objectName: String,
): String {
    val qualifiedName = sqlQualifiedName(schemaName, objectName)
    return """
        select count(*) as total_rows
        from $qualifiedName;
    """.trimIndent()
}

private fun buildSqlConsoleObjectsHref(
    workspaceId: String,
    query: String? = null,
    sourceName: String? = null,
    schemaName: String? = null,
    objectName: String? = null,
    objectType: String? = null,
    inspectorTab: String? = null,
): String {
    val queryParams = buildList {
        add("workspaceId=${urlEncode(workspaceId)}")
        query?.takeIf { it.isNotBlank() }?.let { add("query=${urlEncode(it)}") }
        sourceName?.takeIf { it.isNotBlank() }?.let { add("source=${urlEncode(it)}") }
        schemaName?.takeIf { it.isNotBlank() }?.let { add("schema=${urlEncode(it)}") }
        objectName?.takeIf { it.isNotBlank() }?.let { add("object=${urlEncode(it)}") }
        objectType?.takeIf { it.isNotBlank() }?.let { add("type=${urlEncode(it)}") }
        inspectorTab?.takeIf { it.isNotBlank() }?.let { add("tab=${urlEncode(it)}") }
    }
    return "/sql-console-objects?${queryParams.joinToString("&")}"
}
