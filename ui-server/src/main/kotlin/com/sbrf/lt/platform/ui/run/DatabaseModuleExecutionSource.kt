package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sbrf.lt.datapool.app.RuntimeModuleSnapshot
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.DriverManagerDatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.normalizeRegistrySchemaName
import com.sbrf.lt.platform.ui.config.UiModuleStorePostgresConfig
import com.sbrf.lt.platform.ui.config.schemaName
import com.sbrf.lt.platform.ui.module.DatabaseModuleSnapshotSupport
import java.util.UUID

/**
 * Готовит runtime snapshot DB-модуля из current revision или personal working copy
 * и одновременно сохраняет execution snapshot в PostgreSQL registry.
 */
class DatabaseModuleExecutionSource(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String = UiModuleStorePostgresConfig.DEFAULT_SCHEMA,
    objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val snapshotFactory: RuntimeConfigSnapshotFactory = RuntimeConfigSnapshotFactory(),
) {
    private val snapshotSupport = DatabaseModuleSnapshotSupport(objectMapper)
    private val querySupport = DatabaseModuleExecutionQuerySupport(snapshotSupport)
    private val persistenceSupport = DatabaseModuleExecutionSnapshotPersistenceSupport()

    companion object {
        fun fromConfig(config: UiModuleStorePostgresConfig): DatabaseModuleExecutionSource =
            DatabaseModuleExecutionSource(
                connectionProvider = DriverManagerDatabaseConnectionProvider(
                    requireNotNull(config.jdbcUrl),
                    requireNotNull(config.username),
                    requireNotNull(config.password),
                ),
                schema = config.schemaName(),
            )
    }

    fun prepareExecution(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): RuntimeModuleSnapshot {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val source = querySupport.loadSource(connection, normalizedSchema, moduleCode, actorId, actorSource)
                val sqlFiles = querySupport.loadSqlFiles(connection, normalizedSchema, source)
                val executionSnapshotId = UUID.randomUUID().toString()
                val runtimeSnapshot = snapshotFactory.createSnapshot(
                    moduleCode = source.moduleCode,
                    moduleTitle = source.title,
                    configText = source.configText,
                    sqlFiles = sqlFiles,
                    launchSourceKind = source.sourceKind,
                    configLocation = "db:${source.moduleCode}#${source.sourceKind.lowercase()}",
                    executionSnapshotId = executionSnapshotId,
                )

                val snapshotJson = snapshotSupport.serializeExecutionSnapshot(source.configText, sqlFiles)
                val contentHash = snapshotSupport.calculateExecutionContentHash(source.configText, sqlFiles)
                persistenceSupport.insertExecutionSnapshot(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    executionSnapshotId = executionSnapshotId,
                    source = source,
                    actorId = actorId,
                    actorSource = actorSource,
                    actorDisplayName = actorDisplayName,
                    snapshotJson = snapshotJson,
                    contentHash = contentHash,
                )
                connection.commit()
                return runtimeSnapshot
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }
}
