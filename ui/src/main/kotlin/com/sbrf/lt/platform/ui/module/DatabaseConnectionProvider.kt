package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.platform.ui.config.UiModuleStorePostgresConfig
import java.sql.Connection
import java.sql.DriverManager

/**
 * Поставщик JDBC-соединений для PostgreSQL registry UI.
 */
fun interface DatabaseConnectionProvider {
    fun getConnection(): Connection
}

/**
 * Реализация поставщика соединений через стандартный `DriverManager`.
 */
class DriverManagerDatabaseConnectionProvider(
    private val config: UiModuleStorePostgresConfig,
) : DatabaseConnectionProvider {
    override fun getConnection(): Connection =
        DriverManager.getConnection(config.jdbcUrl, config.username, config.password)
}
