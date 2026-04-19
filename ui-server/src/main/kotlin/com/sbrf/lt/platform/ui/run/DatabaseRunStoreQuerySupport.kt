package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.normalizeRegistrySchemaName
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunSummaryResponse

internal class DatabaseRunStoreQuerySupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String,
    private val objectMapperWithTime: ObjectMapper,
) : DatabaseRunQueryStore {
    private val normalizedSchema = normalizeRegistrySchemaName(schema)
    private val runOverviewSupport = DatabaseRunStoreRunOverviewSupport(
        connectionProvider = connectionProvider,
        normalizedSchema = normalizedSchema,
    )
    private val runDetailsSupport = DatabaseRunStoreRunDetailsSupport(
        connectionProvider = connectionProvider,
        normalizedSchema = normalizedSchema,
        objectMapperWithTime = objectMapperWithTime,
    )

    override fun activeModuleCodes(): Set<String> = runOverviewSupport.activeModuleCodes()

    override fun listRuns(moduleCode: String, limit: Int): List<DatabaseModuleRunSummaryResponse> =
        runOverviewSupport.listRuns(moduleCode, limit)

    override fun loadRunDetails(moduleCode: String, runId: String): DatabaseModuleRunDetailsResponse =
        runDetailsSupport.loadRunDetails(moduleCode, runId)
}

internal fun java.sql.ResultSet.toRunSummary(): DatabaseModuleRunSummaryResponse =
    DatabaseModuleRunSummaryResponse(
        runId = getString("run_id"),
        executionSnapshotId = getString("execution_snapshot_id"),
        status = getString("status"),
        launchSourceKind = getString("launch_source_kind"),
        requestedAt = getTimestamp("requested_at").toInstant(),
        startedAt = getTimestamp("started_at")?.toInstant(),
        finishedAt = getTimestamp("finished_at")?.toInstant(),
        moduleCode = getString("module_code_snapshot"),
        moduleTitle = getString("module_title_snapshot"),
        outputDir = getString("output_dir"),
        mergedRowCount = getLong("merged_row_count").takeIf { !wasNull() },
        successfulSourceCount = getInt("successful_source_count"),
        failedSourceCount = getInt("failed_source_count"),
        skippedSourceCount = getInt("skipped_source_count"),
        targetStatus = getString("target_status"),
        targetTableName = getString("target_table_name"),
        targetRowsLoaded = getLong("target_rows_loaded").takeIf { !wasNull() },
        errorMessage = getString("error_message"),
    )
