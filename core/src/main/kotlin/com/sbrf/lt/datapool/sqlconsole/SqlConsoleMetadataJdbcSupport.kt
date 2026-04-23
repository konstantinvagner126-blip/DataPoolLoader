package com.sbrf.lt.datapool.sqlconsole

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

class JdbcShardSqlObjectSearcher : ShardSqlObjectSearcher, ShardSqlObjectInspector, ShardSqlObjectColumnLoader {
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

    override fun inspectObject(
        shard: ResolvedSqlConsoleShardConfig,
        schemaName: String,
        objectName: String,
        objectType: SqlConsoleDatabaseObjectType,
    ): SqlConsoleDatabaseObjectInspector {
        DriverManager.getConnection(shard.jdbcUrl, shard.username, shard.password).use { connection ->
            val databaseProductName = connection.metaData.databaseProductName.orEmpty()
            return if (databaseProductName.contains("postgres", ignoreCase = true)) {
                inspectPostgres(connection, schemaName, objectName, objectType)
            } else {
                inspectGeneric(connection, schemaName, objectName, objectType)
            }
        }
    }

    override fun loadObjectColumns(
        shard: ResolvedSqlConsoleShardConfig,
        schemaName: String,
        objectName: String,
        objectType: SqlConsoleDatabaseObjectType,
    ): List<SqlConsoleDatabaseObjectColumn> {
        DriverManager.getConnection(shard.jdbcUrl, shard.username, shard.password).use { connection ->
            val databaseProductName = connection.metaData.databaseProductName.orEmpty()
            return if (databaseProductName.contains("postgres", ignoreCase = true)) {
                loadPostgresObjectColumns(connection, schemaName, objectName, objectType)
            } else {
                loadGenericObjectColumns(connection, schemaName, objectName, objectType)
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

    private fun inspectPostgres(
        connection: Connection,
        schemaName: String,
        objectName: String,
        objectType: SqlConsoleDatabaseObjectType,
    ): SqlConsoleDatabaseObjectInspector =
        when (objectType) {
            SqlConsoleDatabaseObjectType.TABLE,
            SqlConsoleDatabaseObjectType.VIEW,
            SqlConsoleDatabaseObjectType.MATERIALIZED_VIEW,
            -> inspectPostgresTableLikeObject(connection, schemaName, objectName, objectType)
            SqlConsoleDatabaseObjectType.INDEX -> inspectPostgresIndexObject(connection, schemaName, objectName)
            SqlConsoleDatabaseObjectType.SEQUENCE -> inspectPostgresSequenceObject(connection, schemaName, objectName)
            SqlConsoleDatabaseObjectType.TRIGGER -> inspectPostgresTriggerObject(connection, schemaName, objectName)
            SqlConsoleDatabaseObjectType.SCHEMA -> inspectPostgresSchemaObject(connection, schemaName)
        }

    private fun loadPostgresObjectColumns(
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

    private fun inspectPostgresTableLikeObject(
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

    private fun inspectPostgresIndexObject(
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

    private fun inspectPostgresSequenceObject(
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

    private fun inspectPostgresTriggerObject(
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

    private fun inspectPostgresSchemaObject(
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

    private fun inspectGeneric(
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

            SqlConsoleDatabaseObjectType.INDEX -> SqlConsoleDatabaseObjectInspector(
                schemaName = schemaName,
                objectName = objectName,
                objectType = objectType,
            )

            else -> SqlConsoleDatabaseObjectInspector(
                schemaName = schemaName,
                objectName = objectName,
                objectType = objectType,
            )
        }

    private fun loadGenericObjectColumns(
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

    private fun ResultSet.toIndexMetadata(): SqlConsoleDatabaseObjectIndex =
        SqlConsoleDatabaseObjectIndex(
            name = getString("indexname"),
            tableName = getString("tablename"),
            columns = getStringArray("column_names"),
            unique = getBoolean("indisunique"),
            primary = getBoolean("indisprimary"),
            definition = getString("indexdef"),
        )

    private fun ResultSet.toTriggerMetadata(): SqlConsoleDatabaseObjectTrigger {
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

    private fun ResultSet.getStringArray(columnLabel: String): List<String> {
        val sqlArray = getArray(columnLabel) ?: return emptyList()
        val raw = sqlArray.array
        return when (raw) {
            is Array<*> -> raw.filterIsInstance<String>()
            is Collection<*> -> raw.filterIsInstance<String>()
            else -> emptyList()
        }
    }

    private fun translateConstraintType(typeCode: String): String =
        when (typeCode) {
            "p" -> "PRIMARY KEY"
            "u" -> "UNIQUE"
            "f" -> "FOREIGN KEY"
            "c" -> "CHECK"
            "x" -> "EXCLUDE"
            else -> typeCode
        }

    private fun decodeTriggerTiming(triggerType: Int): String =
        when {
            triggerType and 64 != 0 -> "INSTEAD OF"
            triggerType and 2 != 0 -> "BEFORE"
            else -> "AFTER"
        }

    private fun decodeTriggerEvents(triggerType: Int): List<String> = buildList {
        if (triggerType and 4 != 0) add("INSERT")
        if (triggerType and 8 != 0) add("DELETE")
        if (triggerType and 16 != 0) add("UPDATE")
        if (triggerType and 32 != 0) add("TRUNCATE")
    }

    private fun buildSyntheticTableDefinition(
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

    private fun buildSequenceDefinition(
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

    private fun sqlIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun sqlQualifiedName(
        schemaName: String,
        objectName: String,
    ): String = "${sqlIdentifier(schemaName)}.${sqlIdentifier(objectName)}"

    private fun objectIdentityKey(value: SqlConsoleDatabaseObject): String =
        listOf(value.schemaName, value.objectName, value.objectType.name, value.tableName.orEmpty()).joinToString("|")
}
