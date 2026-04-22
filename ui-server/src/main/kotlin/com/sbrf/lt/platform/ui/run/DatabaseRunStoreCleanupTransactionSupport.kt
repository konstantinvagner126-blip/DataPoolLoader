package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider

internal class DatabaseRunStoreCleanupTransactionSupport(
    private val connectionProvider: DatabaseConnectionProvider,
) {
    fun <T> inTransaction(block: (java.sql.Connection) -> T): T {
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val result = block(connection)
                connection.commit()
                return result
            } catch (ex: Exception) {
                connection.rollback()
                throw ex
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }
}
