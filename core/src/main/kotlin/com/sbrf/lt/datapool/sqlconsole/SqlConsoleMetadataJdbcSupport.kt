package com.sbrf.lt.datapool.sqlconsole

import java.sql.Connection
import java.sql.DriverManager

class JdbcShardSqlObjectSearcher : ShardSqlObjectSearcher {
    override fun searchObjects(
        shard: ResolvedSqlConsoleShardConfig,
        rawQuery: String,
        maxObjects: Int,
    ): ShardSqlObjectSearchResult {
        DriverManager.getConnection(shard.jdbcUrl, shard.username, shard.password).use { connection ->
            val databaseProductName = connection.metaData.databaseProductName.orEmpty()
            return if (databaseProductName.contains("postgres", ignoreCase = true)) {
                searchPostgres(connection, rawQuery.trim(), maxObjects)
            } else {
                searchGeneric(connection, rawQuery.trim(), maxObjects)
            }
        }
    }

    private fun searchPostgres(
        connection: Connection,
        query: String,
        maxObjects: Int,
    ): ShardSqlObjectSearchResult {
        val tableLikeObjects = loadPostgresTableLikeObjects(connection, query, maxObjects)
        val indexObjects = loadPostgresIndexObjects(connection, query, maxObjects)
        val combined = (tableLikeObjects + indexObjects)
            .distinctBy { listOf(it.schemaName, it.objectName, it.objectType.name, it.tableName).joinToString("|") }
            .sortedWith(
                compareBy<SqlConsoleDatabaseObject> { it.schemaName.lowercase() }
                    .thenBy { it.objectName.lowercase() }
                    .thenBy { it.objectType.name },
            )
        return ShardSqlObjectSearchResult(
            objects = combined.take(maxObjects),
            truncated = combined.size > maxObjects,
        )
    }

    private fun loadPostgresTableLikeObjects(
        connection: Connection,
        query: String,
        maxObjects: Int,
    ): List<SqlConsoleDatabaseObject> {
        val likePattern = "%$query%"
        val sql =
            """
            select table_schema, table_name, case when table_type = 'BASE TABLE' then 'TABLE' else 'VIEW' end as object_type
            from information_schema.tables
            where table_schema not in ('pg_catalog', 'information_schema')
              and (table_schema ilike ? or table_name ilike ?)
            union all
            select schemaname as table_schema, matviewname as table_name, 'MATERIALIZED_VIEW' as object_type
            from pg_matviews
            where schemaname not in ('pg_catalog', 'information_schema')
              and (schemaname ilike ? or matviewname ilike ?)
            order by 1, 2
            limit ?
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, likePattern)
            statement.setString(2, likePattern)
            statement.setString(3, likePattern)
            statement.setString(4, likePattern)
            statement.setInt(5, maxObjects + 1)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        val schemaName = resultSet.getString("table_schema")
                        val tableName = resultSet.getString("table_name")
                        add(
                            SqlConsoleDatabaseObject(
                                schemaName = schemaName,
                                objectName = tableName,
                                objectType = SqlConsoleDatabaseObjectType.valueOf(resultSet.getString("object_type")),
                                columns = loadPostgresColumns(connection, schemaName, tableName),
                                indexNames = loadPostgresIndexNames(connection, schemaName, tableName),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun loadPostgresIndexObjects(
        connection: Connection,
        query: String,
        maxObjects: Int,
    ): List<SqlConsoleDatabaseObject> {
        val likePattern = "%$query%"
        val sql =
            """
            select schemaname, indexname, tablename, indexdef
            from pg_indexes
            where schemaname not in ('pg_catalog', 'information_schema')
              and (schemaname ilike ? or tablename ilike ? or indexname ilike ?)
            order by schemaname, indexname
            limit ?
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, likePattern)
            statement.setString(2, likePattern)
            statement.setString(3, likePattern)
            statement.setInt(4, maxObjects + 1)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            SqlConsoleDatabaseObject(
                                schemaName = resultSet.getString("schemaname"),
                                objectName = resultSet.getString("indexname"),
                                objectType = SqlConsoleDatabaseObjectType.INDEX,
                                tableName = resultSet.getString("tablename"),
                                definition = resultSet.getString("indexdef"),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun loadPostgresColumns(
        connection: Connection,
        schemaName: String,
        tableName: String,
    ): List<SqlConsoleDatabaseObjectColumn> {
        val sql =
            """
            select column_name, data_type, is_nullable
            from information_schema.columns
            where table_schema = ? and table_name = ?
            order by ordinal_position
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, tableName)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            SqlConsoleDatabaseObjectColumn(
                                name = resultSet.getString("column_name"),
                                type = resultSet.getString("data_type"),
                                nullable = resultSet.getString("is_nullable").equals("YES", ignoreCase = true),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun loadPostgresIndexNames(
        connection: Connection,
        schemaName: String,
        tableName: String,
    ): List<String> {
        val sql =
            """
            select indexname
            from pg_indexes
            where schemaname = ? and tablename = ?
            order by indexname
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, tableName)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.getString("indexname"))
                    }
                }
            }
        }
    }

    private fun searchGeneric(
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
                val indexNames = mutableListOf<String>()
                val indexObjects = mutableListOf<SqlConsoleDatabaseObject>()
                metadata.getIndexInfo(null, schemaName, tableName, false, false).use { indexResultSet ->
                    while (indexResultSet.next()) {
                        val indexName = indexResultSet.getString("INDEX_NAME") ?: continue
                        if (indexName !in indexNames) {
                            indexNames += indexName
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
                }
                objects += SqlConsoleDatabaseObject(
                    schemaName = schemaName,
                    objectName = tableName,
                    objectType = if (type.equals("VIEW", ignoreCase = true)) {
                        SqlConsoleDatabaseObjectType.VIEW
                    } else {
                        SqlConsoleDatabaseObjectType.TABLE
                    },
                    columns = metadata.getColumns(null, schemaName, tableName, null).use { columnsResultSet ->
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
                    },
                    indexNames = indexNames.sorted(),
                )
                objects += indexObjects
            }
        }
        val distinctObjects = objects
            .distinctBy { listOf(it.schemaName, it.objectName, it.objectType.name, it.tableName).joinToString("|") }
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
}
