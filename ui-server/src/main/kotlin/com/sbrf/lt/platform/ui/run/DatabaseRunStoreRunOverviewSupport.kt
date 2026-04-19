package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunSummaryResponse

internal class DatabaseRunStoreRunOverviewSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val normalizedSchema: String,
) {
    fun activeModuleCodes(): Set<String> {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.listActiveModuleCodes(normalizedSchema)).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val result = linkedSetOf<String>()
                    while (rs.next()) {
                        result += rs.getString("module_code")
                    }
                    return result
                }
            }
        }
    }

    fun listRuns(moduleCode: String, limit: Int): List<DatabaseModuleRunSummaryResponse> {
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.listRuns(normalizedSchema)).use { stmt ->
                stmt.setString(1, moduleCode)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<DatabaseModuleRunSummaryResponse>()
                    while (rs.next()) {
                        result += rs.toRunSummary()
                    }
                    return result
                }
            }
        }
    }
}

