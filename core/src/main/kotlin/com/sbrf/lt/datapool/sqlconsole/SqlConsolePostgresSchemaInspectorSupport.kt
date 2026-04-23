package com.sbrf.lt.datapool.sqlconsole

import java.sql.Connection

internal class SqlConsolePostgresSchemaInspectorSupport {
    fun inspectObject(
        connection: Connection,
        schemaName: String,
    ): SqlConsoleDatabaseObjectInspector {
        val schema = loadPostgresSchemaDetails(connection, schemaName)
        return SqlConsoleDatabaseObjectInspector(
            schemaName = schemaName,
            objectName = schemaName,
            objectType = SqlConsoleDatabaseObjectType.SCHEMA,
            definition = "create schema if not exists ${sqlIdentifier(schemaName)};",
            schema = schema,
        )
    }

    private fun loadPostgresSchemaDetails(
        connection: Connection,
        schemaName: String,
    ): SqlConsoleDatabaseObjectSchema {
        val ownerAndComment = connection.prepareStatement(
            """
            select pg_get_userbyid(nspowner) as owner,
                   obj_description(oid, 'pg_namespace') as comment
            from pg_namespace
            where nspname = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, schemaName)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) {
                    resultSet.getString("owner") to resultSet.getString("comment")
                } else {
                    null to null
                }
            }
        }
        val privileges = connection.prepareStatement(
            """
            select grantee, privilege_type
            from information_schema.schema_privileges
            where schema_name = ?
            order by grantee, privilege_type
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, schemaName)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add("${resultSet.getString("grantee")}: ${resultSet.getString("privilege_type")}")
                    }
                }
            }
        }
        val objectCounts = buildList {
            add(SqlConsoleDatabaseObjectCount("Таблицы", countPostgresRelations(connection, schemaName, "r")))
            add(SqlConsoleDatabaseObjectCount("Представления", countPostgresRelations(connection, schemaName, "v")))
            add(SqlConsoleDatabaseObjectCount("Материализованные представления", countPostgresRelations(connection, schemaName, "m")))
            add(SqlConsoleDatabaseObjectCount("Индексы", countPostgresIndexes(connection, schemaName)))
            add(SqlConsoleDatabaseObjectCount("Последовательности", countPostgresRelations(connection, schemaName, "S")))
            add(SqlConsoleDatabaseObjectCount("Триггеры", countPostgresTriggers(connection, schemaName)))
        }
        return SqlConsoleDatabaseObjectSchema(
            owner = ownerAndComment.first,
            comment = ownerAndComment.second,
            privileges = privileges,
            objectCounts = objectCounts,
        )
    }

    private fun countPostgresRelations(
        connection: Connection,
        schemaName: String,
        relKind: String,
    ): Int =
        connection.prepareStatement(
            """
            select count(*)
            from pg_class cls
            join pg_namespace ns on ns.oid = cls.relnamespace
            where ns.nspname = ? and cls.relkind = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, relKind)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.getInt(1) else 0
            }
        }

    private fun countPostgresIndexes(
        connection: Connection,
        schemaName: String,
    ): Int =
        connection.prepareStatement(
            """
            select count(*)
            from pg_indexes
            where schemaname = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, schemaName)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.getInt(1) else 0
            }
        }

    private fun countPostgresTriggers(
        connection: Connection,
        schemaName: String,
    ): Int =
        connection.prepareStatement(
            """
            select count(*)
            from pg_trigger tg
            join pg_class cls on cls.oid = tg.tgrelid
            join pg_namespace ns on ns.oid = cls.relnamespace
            where not tg.tgisinternal and ns.nspname = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, schemaName)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.getInt(1) else 0
            }
        }
}
