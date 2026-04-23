package com.sbrf.lt.datapool.sqlconsole

import java.sql.Connection

internal class SqlConsolePostgresTableLikeInspectorSupport {
    fun inspectObject(
        connection: Connection,
        schemaName: String,
        objectName: String,
        objectType: SqlConsoleDatabaseObjectType,
    ): SqlConsoleDatabaseObjectInspector {
        val columns = loadPostgresColumns(connection, schemaName, objectName)
        val constraints = loadPostgresConstraints(connection, schemaName, objectName)
        val indexes = loadPostgresIndexesForTable(connection, schemaName, objectName)
        val triggers = loadPostgresRelatedTriggers(connection, schemaName, objectName)
        val definition = when (objectType) {
            SqlConsoleDatabaseObjectType.VIEW -> loadPostgresViewDefinition(connection, schemaName, objectName)
            SqlConsoleDatabaseObjectType.MATERIALIZED_VIEW -> loadPostgresMaterializedViewDefinition(connection, schemaName, objectName)
            SqlConsoleDatabaseObjectType.TABLE -> buildSyntheticTableDefinition(schemaName, objectName, columns, constraints)
            else -> null
        }
        return SqlConsoleDatabaseObjectInspector(
            schemaName = schemaName,
            objectName = objectName,
            objectType = objectType,
            definition = definition,
            columns = columns,
            indexes = indexes,
            constraints = constraints,
            relatedTriggers = triggers,
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
            -> loadPostgresColumns(connection, schemaName, objectName)
            else -> emptyList()
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

    private fun loadPostgresConstraints(
        connection: Connection,
        schemaName: String,
        tableName: String,
    ): List<SqlConsoleDatabaseObjectConstraint> {
        val sql =
            """
            select c.conname,
                   c.contype,
                   pg_get_constraintdef(c.oid, true) as definition,
                   array_remove(array_agg(att.attname order by key_positions.ordinality), null) as column_names
            from pg_constraint c
            join pg_class rel on rel.oid = c.conrelid
            join pg_namespace ns on ns.oid = rel.relnamespace
            left join unnest(c.conkey) with ordinality as key_positions(attnum, ordinality) on true
            left join pg_attribute att on att.attrelid = rel.oid and att.attnum = key_positions.attnum
            where ns.nspname = ? and rel.relname = ?
            group by c.oid, c.conname, c.contype
            order by c.conname
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, tableName)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            SqlConsoleDatabaseObjectConstraint(
                                name = resultSet.getString("conname"),
                                type = translateConstraintType(resultSet.getString("contype")),
                                columns = resultSet.getStringArray("column_names"),
                                definition = resultSet.getString("definition"),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun loadPostgresIndexesForTable(
        connection: Connection,
        schemaName: String,
        tableName: String,
    ): List<SqlConsoleDatabaseObjectIndex> {
        val sql =
            """
            select idx.indexname,
                   idx.indexdef,
                   idx.tablename,
                   i.indisunique,
                   i.indisprimary,
                   array_remove(array_agg(att.attname order by idx_keys.ordinality), null) as column_names
            from pg_indexes idx
            join pg_namespace table_ns on table_ns.nspname = idx.schemaname
            join pg_class table_rel on table_rel.relname = idx.tablename and table_rel.relnamespace = table_ns.oid
            join pg_class index_rel on index_rel.relname = idx.indexname and index_rel.relnamespace = table_ns.oid
            join pg_index i on i.indexrelid = index_rel.oid and i.indrelid = table_rel.oid
            left join unnest(i.indkey) with ordinality as idx_keys(attnum, ordinality) on idx_keys.attnum > 0
            left join pg_attribute att on att.attrelid = table_rel.oid and att.attnum = idx_keys.attnum
            where idx.schemaname = ? and idx.tablename = ?
            group by idx.indexname, idx.indexdef, idx.tablename, i.indisunique, i.indisprimary
            order by idx.indexname
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, tableName)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.toIndexMetadata())
                    }
                }
            }
        }
    }

    private fun loadPostgresRelatedTriggers(
        connection: Connection,
        schemaName: String,
        tableName: String,
    ): List<SqlConsoleDatabaseObjectTrigger> {
        val sql =
            """
            select tg.tgname,
                   cls.relname as table_name,
                   tg.tgtype,
                   tg.tgenabled,
                   proc.proname as function_name,
                   pg_get_triggerdef(tg.oid, true) as definition
            from pg_trigger tg
            join pg_class cls on cls.oid = tg.tgrelid
            join pg_namespace ns on ns.oid = cls.relnamespace
            join pg_proc proc on proc.oid = tg.tgfoid
            where not tg.tgisinternal
              and ns.nspname = ?
              and cls.relname = ?
            order by tg.tgname
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, tableName)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(resultSet.toTriggerMetadata())
                    }
                }
            }
        }
    }

    private fun loadPostgresViewDefinition(
        connection: Connection,
        schemaName: String,
        viewName: String,
    ): String? {
        val sql =
            """
            select definition
            from pg_views
            where schemaname = ? and viewname = ?
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, viewName)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.getString("definition") else null
            }
        }
    }

    private fun loadPostgresMaterializedViewDefinition(
        connection: Connection,
        schemaName: String,
        viewName: String,
    ): String? {
        val sql =
            """
            select definition
            from pg_matviews
            where schemaname = ? and matviewname = ?
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, viewName)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.getString("definition") else null
            }
        }
    }
}
