package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse

internal class DatabaseRunStoreRunDetailsSupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val normalizedSchema: String,
    private val objectMapperWithTime: ObjectMapper,
) {
    private val summaryQuerySupport = DatabaseRunStoreRunSummaryQuerySupport(normalizedSchema)
    private val relatedQuerySupport = DatabaseRunStoreRunRelatedQuerySupport(
        normalizedSchema = normalizedSchema,
        objectMapperWithTime = objectMapperWithTime,
    )

    fun loadRunDetails(moduleCode: String, runId: String): DatabaseModuleRunDetailsResponse {
        connectionProvider.getConnection().use { connection ->
            val summary = summaryQuerySupport.loadSummary(connection, moduleCode, runId)
            val sourceResults = relatedQuerySupport.loadSourceResults(connection, runId)
            val events = relatedQuerySupport.loadEvents(connection, runId)
            val artifacts = relatedQuerySupport.loadArtifacts(connection, runId)

            return summary.copy(
                sourceResults = sourceResults,
                events = events,
                artifacts = artifacts,
            )
        }
    }
}
