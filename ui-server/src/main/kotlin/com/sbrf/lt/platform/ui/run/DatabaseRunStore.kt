package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.DriverManagerDatabaseConnectionProvider
import com.sbrf.lt.platform.ui.config.UiModuleStorePostgresConfig
import com.sbrf.lt.platform.ui.config.schemaName

/**
 * Доступ к таблицам DB run-history.
 */
open class DatabaseRunStore(
    executionStore: DatabaseRunExecutionStore,
    queryStore: DatabaseRunQueryStore,
    maintenanceStore: DatabaseRunMaintenanceStore,
) : DatabaseRunExecutionStore by executionStore,
    DatabaseRunQueryStore by queryStore,
    DatabaseRunMaintenanceStore by maintenanceStore {

    constructor(
        connectionProvider: DatabaseConnectionProvider,
        schema: String = UiModuleStorePostgresConfig.DEFAULT_SCHEMA,
        objectMapper: ObjectMapper = jacksonObjectMapper(),
    ) : this(
        executionStore = DatabaseRunStoreExecutionSupport(
            connectionProvider = connectionProvider,
            schema = schema,
            objectMapperWithTime = createRunStoreObjectMapper(objectMapper),
        ),
        queryStore = DatabaseRunStoreQuerySupport(
            connectionProvider = connectionProvider,
            schema = schema,
            objectMapperWithTime = createRunStoreObjectMapper(objectMapper),
        ),
        maintenanceStore = DatabaseRunStoreMaintenanceSupport(
            connectionProvider = connectionProvider,
            schema = schema,
        ),
    )

    companion object {
        fun fromConfig(config: UiModuleStorePostgresConfig): DatabaseRunStore =
            DatabaseRunStore(
                connectionProvider = DriverManagerDatabaseConnectionProvider(
                    requireNotNull(config.jdbcUrl),
                    requireNotNull(config.username),
                    requireNotNull(config.password),
                ),
                schema = config.schemaName(),
            )
    }
}
