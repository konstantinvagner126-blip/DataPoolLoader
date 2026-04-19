package com.sbrf.lt.platform.composeui.sql_console

internal fun SqlConsoleFavoriteObject.qualifiedName(): String = "${schemaName}.${objectName}"

internal fun SqlConsoleDatabaseObject.qualifiedName(): String = "${schemaName}.${objectName}"

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

internal fun buildFavoriteMetadataHref(favorite: SqlConsoleFavoriteObject): String =
    "/sql-console-objects?query=${urlEncode(favorite.objectName)}&source=${urlEncode(favorite.sourceName)}&schema=${urlEncode(favorite.schemaName)}&object=${urlEncode(favorite.objectName)}&type=${urlEncode(favorite.objectType)}"

internal fun translateSqlObjectType(type: String): String =
    when (type.uppercase()) {
        "TABLE" -> "Таблица"
        "VIEW" -> "Представление"
        "MATERIALIZED_VIEW" -> "Материализованное представление"
        "INDEX" -> "Индекс"
        else -> type
    }

internal fun sqlQualifiedName(
    schemaName: String,
    objectName: String,
): String = "${sqlIdentifier(schemaName)}.${sqlIdentifier(objectName)}"

internal fun sqlIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""

internal fun sqlLiteral(value: String): String = "'${value.replace("'", "''")}'"

internal fun urlEncode(value: String): String = js("encodeURIComponent(value)") as String

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
