package com.sbrf.lt.datapool.sqlconsole

import java.sql.Connection

internal class SqlConsoleGenericObjectMetadataSupport {
    fun search(
        connection: Connection,
        query: String,
        maxObjects: Int,
    ): ShardSqlObjectSearchResult {
        val metadata = connection.metaData
        val pattern = "%$query%"
        val objects = mutableListOf<SqlConsoleDatabaseObject>()
        metadata.getTables(null, null, pattern, arrayOf("TABLE", "VIEW")).use { resultSet ->
            while (resultSet.next() && objects.size < maxObjects + 1) {
                val schemaName = resultSet.getString("TABLE_SCHEM") ?: "public"
                val tableName = resultSet.getString("TABLE_NAME")
                val type = resultSet.getString("TABLE_TYPE")
                val indexObjects = mutableListOf<SqlConsoleDatabaseObject>()
                metadata.getIndexInfo(null, schemaName, tableName, false, false).use { indexResultSet ->
                    val seenIndexNames = mutableSetOf<String>()
                    while (indexResultSet.next()) {
                        val indexName = indexResultSet.getString("INDEX_NAME") ?: continue
                        if (!seenIndexNames.add(indexName)) {
                            continue
                        }
                        if (indexName.contains(query, ignoreCase = true)) {
                            indexObjects += SqlConsoleDatabaseObject(
                                schemaName = schemaName,
                                objectName = indexName,
                                objectType = SqlConsoleDatabaseObjectType.INDEX,
                                tableName = tableName,
                            )
                        }
                    }
                }
                objects += SqlConsoleDatabaseObject(
                    schemaName = schemaName,
                    objectName = tableName,
                    objectType = if (type.equals("VIEW", ignoreCase = true)) {
                        SqlConsoleDatabaseObjectType.VIEW
                    } else {
                        SqlConsoleDatabaseObjectType.TABLE
                    },
                )
                objects += indexObjects
            }
        }
        val distinctObjects = objects
            .distinctBy(::objectIdentityKey)
            .sortedWith(
                compareBy<SqlConsoleDatabaseObject> { it.schemaName.lowercase() }
                    .thenBy { it.objectName.lowercase() }
                    .thenBy { it.objectType.name },
            )
        return ShardSqlObjectSearchResult(
            objects = distinctObjects.take(maxObjects),
            truncated = distinctObjects.size > maxObjects,
        )
    }

    fun inspectObject(
        connection: Connection,
        schemaName: String,
        objectName: String,
        objectType: SqlConsoleDatabaseObjectType,
    ): SqlConsoleDatabaseObjectInspector =
        when (objectType) {
            SqlConsoleDatabaseObjectType.TABLE,
            SqlConsoleDatabaseObjectType.VIEW,
            -> {
                val metadata = connection.metaData
                val columns = metadata.getColumns(null, schemaName, objectName, null).use { columnsResultSet ->
                    buildList {
                        while (columnsResultSet.next()) {
                            add(
                                SqlConsoleDatabaseObjectColumn(
                                    name = columnsResultSet.getString("COLUMN_NAME"),
                                    type = columnsResultSet.getString("TYPE_NAME"),
                                    nullable = columnsResultSet.getInt("NULLABLE") != 0,
                                ),
                            )
                        }
                    }
                }
                val indexes = metadata.getIndexInfo(null, schemaName, objectName, false, false).use { indexResultSet ->
                    val indexMap = linkedMapOf<String, MutableList<String>>()
                    while (indexResultSet.next()) {
                        val indexName = indexResultSet.getString("INDEX_NAME") ?: continue
                        val columnName = indexResultSet.getString("COLUMN_NAME")
                        indexMap.getOrPut(indexName) { mutableListOf() }.apply {
                            if (!columnName.isNullOrBlank()) {
                                add(columnName)
                            }
                        }
                    }
                    indexMap.map { (name, columns) ->
                        SqlConsoleDatabaseObjectIndex(name = name, tableName = objectName, columns = columns)
                    }
                }
                SqlConsoleDatabaseObjectInspector(
                    schemaName = schemaName,
                    objectName = objectName,
                    objectType = objectType,
                    columns = columns,
                    indexes = indexes,
                )
            }

            else -> SqlConsoleDatabaseObjectInspector(
                schemaName = schemaName,
                objectName = objectName,
                objectType = objectType,
            )
        }

    fun loadObjectColumns(
        connection: Connection,
        schemaName: String,
        objectName: String,
        objectType: SqlConsoleDatabaseObjectType,
    ): List<SqlConsoleDatabaseObjectColumn> =
        when (objectType) {
            SqlConsoleDatabaseObjectType.TABLE,
            SqlConsoleDatabaseObjectType.VIEW,
            SqlConsoleDatabaseObjectType.MATERIALIZED_VIEW,
            -> connection.metaData.getColumns(null, schemaName, objectName, null).use { columnsResultSet ->
                buildList {
                    while (columnsResultSet.next()) {
                        add(
                            SqlConsoleDatabaseObjectColumn(
                                name = columnsResultSet.getString("COLUMN_NAME"),
                                type = columnsResultSet.getString("TYPE_NAME"),
                                nullable = columnsResultSet.getInt("NULLABLE") != 0,
                            ),
                        )
                    }
                }
            }
            else -> emptyList()
        }
}
