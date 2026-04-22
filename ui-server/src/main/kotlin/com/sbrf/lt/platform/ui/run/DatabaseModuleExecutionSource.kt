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
    private val runtimeSnapshotSupport = DatabaseModuleExecutionRuntimeSnapshotSupport(
        snapshotFactory = snapshotFactory,
        snapshotSupport = snapshotSupport,
        querySupport = querySupport,
    )

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
                val preparedSnapshot = runtimeSnapshotSupport.prepareRuntimeSnapshot(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    moduleCode = moduleCode,
                    actorId = actorId,
                    actorSource = actorSource,
                )
                persistenceSupport.insertExecutionSnapshot(
                    connection = connection,
                    normalizedSchema = normalizedSchema,
                    executionSnapshotId = preparedSnapshot.executionSnapshotId,
                    source = preparedSnapshot.source,
                    actorId = actorId,
                    actorSource = actorSource,
                    actorDisplayName = actorDisplayName,
                    snapshotJson = preparedSnapshot.snapshotJson,
                    contentHash = preparedSnapshot.contentHash,
                )
                connection.commit()
                return preparedSnapshot.runtimeSnapshot
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }
}
