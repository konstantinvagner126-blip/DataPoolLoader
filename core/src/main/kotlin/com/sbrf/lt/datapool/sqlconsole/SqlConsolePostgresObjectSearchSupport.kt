package com.sbrf.lt.datapool.sqlconsole

import java.sql.Connection

internal class SqlConsolePostgresObjectSearchSupport {
    fun search(
        connection: Connection,
        query: String,
        maxObjects: Int,
    ): ShardSqlObjectSearchResult {
        val tableLikeObjects = loadPostgresTableLikeObjects(connection, query, maxObjects)
        val indexObjects = loadPostgresIndexObjects(connection, query, maxObjects)
        val sequenceObjects = loadPostgresSequenceObjects(connection, query, maxObjects)
        val triggerObjects = loadPostgresTriggerObjects(connection, query, maxObjects)
        val schemaObjects = loadPostgresSchemaObjects(connection, query, maxObjects)
        val combined = (tableLikeObjects + indexObjects + sequenceObjects + triggerObjects + schemaObjects)
            .distinctBy(::objectIdentityKey)
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
                        add(
                            SqlConsoleDatabaseObject(
                                schemaName = resultSet.getString("table_schema"),
                                objectName = resultSet.getString("table_name"),
                                objectType = SqlConsoleDatabaseObjectType.valueOf(resultSet.getString("object_type")),
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
            select schemaname, indexname, tablename
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
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun loadPostgresSequenceObjects(
        connection: Connection,
        query: String,
        maxObjects: Int,
    ): List<SqlConsoleDatabaseObject> {
        val likePattern = "%$query%"
        val sql =
            """
            select schemaname, sequencename
            from pg_sequences
            where schemaname not in ('pg_catalog', 'information_schema')
              and (schemaname ilike ? or sequencename ilike ?)
            order by schemaname, sequencename
            limit ?
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, likePattern)
            statement.setString(2, likePattern)
            statement.setInt(3, maxObjects + 1)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            SqlConsoleDatabaseObject(
                                schemaName = resultSet.getString("schemaname"),
                                objectName = resultSet.getString("sequencename"),
                                objectType = SqlConsoleDatabaseObjectType.SEQUENCE,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun loadPostgresTriggerObjects(
        connection: Connection,
        query: String,
        maxObjects: Int,
    ): List<SqlConsoleDatabaseObject> {
        val likePattern = "%$query%"
        val sql =
            """
            select ns.nspname as schema_name, tg.tgname as trigger_name, cls.relname as table_name
            from pg_trigger tg
            join pg_class cls on cls.oid = tg.tgrelid
            join pg_namespace ns on ns.oid = cls.relnamespace
            where not tg.tgisinternal
              and ns.nspname not in ('pg_catalog', 'information_schema')
              and (ns.nspname ilike ? or cls.relname ilike ? or tg.tgname ilike ?)
            order by ns.nspname, tg.tgname
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
                                schemaName = resultSet.getString("schema_name"),
                                objectName = resultSet.getString("trigger_name"),
                                objectType = SqlConsoleDatabaseObjectType.TRIGGER,
                                tableName = resultSet.getString("table_name"),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun loadPostgresSchemaObjects(
        connection: Connection,
        query: String,
        maxObjects: Int,
    ): List<SqlConsoleDatabaseObject> {
        val likePattern = "%$query%"
        val sql =
            """
            select schema_name
            from information_schema.schemata
            where schema_name not in ('pg_catalog', 'information_schema')
              and schema_name ilike ?
            order by schema_name
            limit ?
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, likePattern)
            statement.setInt(2, maxObjects + 1)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        val schemaName = resultSet.getString("schema_name")
                        add(
                            SqlConsoleDatabaseObject(
                                schemaName = schemaName,
                                objectName = schemaName,
                                objectType = SqlConsoleDatabaseObjectType.SCHEMA,
                            ),
                        )
                    }
                }
            }
        }
    }
}
