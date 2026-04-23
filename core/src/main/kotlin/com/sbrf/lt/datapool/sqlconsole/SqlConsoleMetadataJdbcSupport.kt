package com.sbrf.lt.datapool.sqlconsole

import java.sql.Connection
import java.sql.DriverManager

class JdbcShardSqlObjectSearcher : ShardSqlObjectSearcher, ShardSqlObjectInspector, ShardSqlObjectColumnLoader {
    private val postgresSearchSupport = SqlConsolePostgresObjectSearchSupport()
    private val postgresTableLikeInspectorSupport = SqlConsolePostgresTableLikeInspectorSupport()
    private val postgresSecondaryInspectorSupport = SqlConsolePostgresSecondaryObjectInspectorSupport()
    private val postgresSchemaInspectorSupport = SqlConsolePostgresSchemaInspectorSupport()
    private val genericMetadataSupport = SqlConsoleGenericObjectMetadataSupport()

    override fun searchObjects(
        shard: ResolvedSqlConsoleShardConfig,
        rawQuery: String,
        maxObjects: Int,
    ): ShardSqlObjectSearchResult {
        DriverManager.getConnection(shard.jdbcUrl, shard.username, shard.password).use { connection ->
            return if (connection.isPostgresProduct()) {
                postgresSearchSupport.search(connection, rawQuery.trim(), maxObjects)
            } else {
                genericMetadataSupport.search(connection, rawQuery.trim(), maxObjects)
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
            return if (connection.isPostgresProduct()) {
                when (objectType) {
                    SqlConsoleDatabaseObjectType.TABLE,
                    SqlConsoleDatabaseObjectType.VIEW,
                    SqlConsoleDatabaseObjectType.MATERIALIZED_VIEW,
                    -> postgresTableLikeInspectorSupport.inspectObject(connection, schemaName, objectName, objectType)
                    SqlConsoleDatabaseObjectType.INDEX,
                    SqlConsoleDatabaseObjectType.SEQUENCE,
                    SqlConsoleDatabaseObjectType.TRIGGER,
                    -> postgresSecondaryInspectorSupport.inspectObject(connection, schemaName, objectName, objectType)
                    SqlConsoleDatabaseObjectType.SCHEMA,
                    -> postgresSchemaInspectorSupport.inspectObject(connection, schemaName)
                }
            } else {
                genericMetadataSupport.inspectObject(connection, schemaName, objectName, objectType)
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
            return if (connection.isPostgresProduct()) {
                postgresTableLikeInspectorSupport.loadObjectColumns(connection, schemaName, objectName, objectType)
            } else {
                genericMetadataSupport.loadObjectColumns(connection, schemaName, objectName, objectType)
            }
        }
    }

    private fun Connection.isPostgresProduct(): Boolean =
        metaData.databaseProductName.orEmpty().contains("postgres", ignoreCase = true)
}
