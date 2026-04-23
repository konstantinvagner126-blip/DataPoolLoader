package com.sbrf.lt.datapool.sqlconsole

import java.sql.Connection

internal class SqlConsolePostgresSecondaryObjectInspectorSupport {
    fun inspectObject(
        connection: Connection,
        schemaName: String,
        objectName: String,
        objectType: SqlConsoleDatabaseObjectType,
    ): SqlConsoleDatabaseObjectInspector =
        when (objectType) {
            SqlConsoleDatabaseObjectType.INDEX -> inspectIndexObject(connection, schemaName, objectName)
            SqlConsoleDatabaseObjectType.SEQUENCE -> inspectSequenceObject(connection, schemaName, objectName)
            SqlConsoleDatabaseObjectType.TRIGGER -> inspectTriggerObject(connection, schemaName, objectName)
            else -> SqlConsoleDatabaseObjectInspector(
                schemaName = schemaName,
                objectName = objectName,
                objectType = objectType,
            )
        }

    private fun inspectIndexObject(
        connection: Connection,
        schemaName: String,
        objectName: String,
    ): SqlConsoleDatabaseObjectInspector {
        val index = loadPostgresIndexDetails(connection, schemaName, objectName)
            ?: return SqlConsoleDatabaseObjectInspector(
                schemaName = schemaName,
                objectName = objectName,
                objectType = SqlConsoleDatabaseObjectType.INDEX,
            )
        return SqlConsoleDatabaseObjectInspector(
            schemaName = schemaName,
            objectName = objectName,
            objectType = SqlConsoleDatabaseObjectType.INDEX,
            tableName = index.tableName,
            definition = index.definition,
            indexes = listOf(index),
        )
    }

    private fun inspectSequenceObject(
        connection: Connection,
        schemaName: String,
        objectName: String,
    ): SqlConsoleDatabaseObjectInspector {
        val sequence = loadPostgresSequenceDetails(connection, schemaName, objectName)
        return SqlConsoleDatabaseObjectInspector(
            schemaName = schemaName,
            objectName = objectName,
            objectType = SqlConsoleDatabaseObjectType.SEQUENCE,
            definition = sequence?.let { buildSequenceDefinition(schemaName, objectName, it) },
            sequence = sequence,
        )
    }

    private fun inspectTriggerObject(
        connection: Connection,
        schemaName: String,
        objectName: String,
    ): SqlConsoleDatabaseObjectInspector {
        val trigger = loadPostgresTriggerDetails(connection, schemaName, objectName)
        return SqlConsoleDatabaseObjectInspector(
            schemaName = schemaName,
            objectName = objectName,
            objectType = SqlConsoleDatabaseObjectType.TRIGGER,
            tableName = trigger?.targetObjectName,
            definition = trigger?.definition,
            trigger = trigger,
        )
    }

    private fun loadPostgresIndexDetails(
        connection: Connection,
        schemaName: String,
        indexName: String,
    ): SqlConsoleDatabaseObjectIndex? {
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
            where idx.schemaname = ? and idx.indexname = ?
            group by idx.indexname, idx.indexdef, idx.tablename, i.indisunique, i.indisprimary
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, indexName)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toIndexMetadata() else null
            }
        }
    }

    private fun loadPostgresSequenceDetails(
        connection: Connection,
        schemaName: String,
        objectName: String,
    ): SqlConsoleDatabaseObjectSequence? {
        val sql =
            """
            select seq.increment_by,
                   seq.min_value,
                   seq.max_value,
                   seq.start_value,
                   seq.cache_size,
                   seq.cycle,
                   owner_ns.nspname as owner_schema,
                   owner_rel.relname as owner_table,
                   owner_att.attname as owner_column
            from pg_sequences seq
            join pg_namespace seq_ns on seq_ns.nspname = seq.schemaname
            join pg_class seq_rel on seq_rel.relname = seq.sequencename
                                  and seq_rel.relnamespace = seq_ns.oid
                                  and seq_rel.relkind = 'S'
            left join pg_depend dep on dep.objid = seq_rel.oid and dep.deptype = 'a'
            left join pg_class owner_rel on owner_rel.oid = dep.refobjid
            left join pg_namespace owner_ns on owner_ns.oid = owner_rel.relnamespace
            left join pg_attribute owner_att on owner_att.attrelid = owner_rel.oid and owner_att.attnum = dep.refobjsubid
            where seq.schemaname = ? and seq.sequencename = ?
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, objectName)
            statement.executeQuery().use { resultSet ->
                if (!resultSet.next()) {
                    null
                } else {
                    val owner = buildString {
                        val ownerSchema = resultSet.getString("owner_schema")
                        val ownerTable = resultSet.getString("owner_table")
                        val ownerColumn = resultSet.getString("owner_column")
                        if (!ownerSchema.isNullOrBlank() && !ownerTable.isNullOrBlank()) {
                            append(ownerSchema)
                            append(".")
                            append(ownerTable)
                            if (!ownerColumn.isNullOrBlank()) {
                                append(".")
                                append(ownerColumn)
                            }
                        }
                    }.ifBlank { null }
                    SqlConsoleDatabaseObjectSequence(
                        incrementBy = resultSet.getString("increment_by"),
                        minimumValue = resultSet.getString("min_value"),
                        maximumValue = resultSet.getString("max_value"),
                        startValue = resultSet.getString("start_value"),
                        cacheSize = resultSet.getString("cache_size"),
                        cycle = resultSet.getBoolean("cycle"),
                        ownedBy = owner,
                    )
                }
            }
        }
    }

    private fun loadPostgresTriggerDetails(
        connection: Connection,
        schemaName: String,
        objectName: String,
    ): SqlConsoleDatabaseObjectTrigger? {
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
              and tg.tgname = ?
            order by cls.relname
            limit 1
            """.trimIndent()
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, schemaName)
            statement.setString(2, objectName)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toTriggerMetadata() else null
            }
        }
    }
}
