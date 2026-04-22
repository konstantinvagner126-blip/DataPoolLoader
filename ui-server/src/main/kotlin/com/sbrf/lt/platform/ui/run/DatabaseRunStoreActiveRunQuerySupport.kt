package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql

internal class DatabaseRunStoreActiveRunQuerySupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val normalizedSchema: String,
) {
    fun hasActiveRun(moduleCode: String): Boolean {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.hasActiveRun(normalizedSchema)).use { stmt ->
                stmt.setString(1, moduleCode)
                stmt.executeQuery().use { rs ->
                    return rs.next() && rs.getInt("active_runs") > 0
                }
            }
        }
    }

    fun activeRunIds(moduleCode: String): List<String> {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.listActiveRunIds(normalizedSchema)).use { stmt ->
                stmt.setString(1, moduleCode)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<String>()
                    while (rs.next()) {
                        result += rs.getString("run_id")
                    }
                    return result
                }
            }
        }
    }
}
