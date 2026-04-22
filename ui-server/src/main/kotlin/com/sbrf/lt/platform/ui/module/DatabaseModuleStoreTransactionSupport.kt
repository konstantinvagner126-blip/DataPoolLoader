package com.sbrf.lt.platform.ui.module

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.ModuleRegistrySql
import com.sbrf.lt.datapool.db.registry.sql.normalizeRegistrySchemaName
import java.sql.Connection

/**
 * Единый transactional boundary для DB module registry mutations.
 */
internal class DatabaseModuleStoreTransactionSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String,
) {
    fun discardWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ) {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(ModuleRegistrySql.discardWorkingCopy(normalizedSchema)).use { statement ->
                statement.setString(1, actorId)
                statement.setString(2, actorSource)
                statement.setString(3, moduleCode)
                statement.executeUpdate()
            }
        }
    }

    fun <T> withTransaction(block: (Connection, String) -> T): T {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                val result = block(connection, normalizedSchema)
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
