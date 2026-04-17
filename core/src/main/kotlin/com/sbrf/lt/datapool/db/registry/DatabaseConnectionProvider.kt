package com.sbrf.lt.datapool.db.registry

import java.sql.Connection
import java.sql.DriverManager

/**
 * Поставщик JDBC-соединений для PostgreSQL registry.
 */
fun interface DatabaseConnectionProvider {
    fun getConnection(): Connection
}

/**
 * Реализация поставщика соединений через стандартный `DriverManager`.
 */
class DriverManagerDatabaseConnectionProvider(
    private val jdbcUrl: String,
    private val username: String,
    private val password: String,
) : DatabaseConnectionProvider {
    override fun getConnection(): Connection =
        DriverManager.getConnection(jdbcUrl, username, password)
}
