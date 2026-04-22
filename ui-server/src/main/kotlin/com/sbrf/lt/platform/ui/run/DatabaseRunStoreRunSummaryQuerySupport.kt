package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import com.sbrf.lt.platform.ui.error.UiEntityNotFoundException
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse

internal class DatabaseRunStoreRunSummaryQuerySupport(
    private val normalizedSchema: String,
) {
    fun loadSummary(
        connection: java.sql.Connection,
        moduleCode: String,
        runId: String,
    ): DatabaseModuleRunDetailsResponse =
        connection.prepareStatement(RunHistorySql.loadRunDetails(normalizedSchema)).use { stmt ->
            stmt.setString(1, moduleCode)
            stmt.setString(2, runId)
            stmt.executeQuery().use { rs ->
                if (!rs.next()) {
                    throw UiEntityNotFoundException("История запуска '$runId' для DB-модуля '$moduleCode' не найдена.")
                }
                DatabaseModuleRunDetailsResponse(
                    run = rs.toRunSummary(),
                    summaryJson = rs.getString("summary_json") ?: "{}",
                    sourceResults = emptyList(),
                    events = emptyList(),
                    artifacts = emptyList(),
                )
            }
        }
}
