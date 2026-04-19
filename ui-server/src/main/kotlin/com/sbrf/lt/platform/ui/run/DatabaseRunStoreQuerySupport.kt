package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.db.registry.DatabaseConnectionProvider
import com.sbrf.lt.datapool.db.registry.sql.RunHistorySql
import com.sbrf.lt.datapool.db.registry.sql.normalizeRegistrySchemaName
import com.sbrf.lt.platform.ui.model.DatabaseRunArtifactResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunEventResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunSourceResultResponse
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunSummaryResponse

internal class DatabaseRunStoreQuerySupport(
    private val connectionProvider: DatabaseConnectionProvider,
    private val schema: String,
    private val objectMapperWithTime: ObjectMapper,
) : DatabaseRunQueryStore {

    override fun activeModuleCodes(): Set<String> {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
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

    override fun listRuns(moduleCode: String, limit: Int): List<DatabaseModuleRunSummaryResponse> {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            connection.prepareStatement(RunHistorySql.listRuns(normalizedSchema)).use { stmt ->
                stmt.setString(1, moduleCode)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<DatabaseModuleRunSummaryResponse>()
                    while (rs.next()) {
                        result += DatabaseModuleRunSummaryResponse(
                            runId = rs.getString("run_id"),
                            executionSnapshotId = rs.getString("execution_snapshot_id"),
                            status = rs.getString("status"),
                            launchSourceKind = rs.getString("launch_source_kind"),
                            requestedAt = rs.getTimestamp("requested_at").toInstant(),
                            startedAt = rs.getTimestamp("started_at")?.toInstant(),
                            finishedAt = rs.getTimestamp("finished_at")?.toInstant(),
                            moduleCode = rs.getString("module_code_snapshot"),
                            moduleTitle = rs.getString("module_title_snapshot"),
                            outputDir = rs.getString("output_dir"),
                            mergedRowCount = rs.getLong("merged_row_count").takeIf { !rs.wasNull() },
                            successfulSourceCount = rs.getInt("successful_source_count"),
                            failedSourceCount = rs.getInt("failed_source_count"),
                            skippedSourceCount = rs.getInt("skipped_source_count"),
                            targetStatus = rs.getString("target_status"),
                            targetTableName = rs.getString("target_table_name"),
                            targetRowsLoaded = rs.getLong("target_rows_loaded").takeIf { !rs.wasNull() },
                            errorMessage = rs.getString("error_message"),
                        )
                    }
                    return result
                }
            }
        }
    }

    override fun loadRunDetails(moduleCode: String, runId: String): DatabaseModuleRunDetailsResponse {
        val normalizedSchema = normalizeRegistrySchemaName(schema)
        connectionProvider.getConnection().use { connection ->
            val summary = connection.prepareStatement(RunHistorySql.loadRunDetails(normalizedSchema)).use { stmt ->
                stmt.setString(1, moduleCode)
                stmt.setString(2, runId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) {
                        null
                    } else {
                        DatabaseModuleRunDetailsResponse(
                            run = DatabaseModuleRunSummaryResponse(
                                runId = rs.getString("run_id"),
                                executionSnapshotId = rs.getString("execution_snapshot_id"),
                                status = rs.getString("status"),
                                launchSourceKind = rs.getString("launch_source_kind"),
                                requestedAt = rs.getTimestamp("requested_at").toInstant(),
                                startedAt = rs.getTimestamp("started_at")?.toInstant(),
                                finishedAt = rs.getTimestamp("finished_at")?.toInstant(),
                                moduleCode = rs.getString("module_code_snapshot"),
                                moduleTitle = rs.getString("module_title_snapshot"),
                                outputDir = rs.getString("output_dir"),
                                mergedRowCount = rs.getLong("merged_row_count").takeIf { !rs.wasNull() },
                                successfulSourceCount = rs.getInt("successful_source_count"),
                                failedSourceCount = rs.getInt("failed_source_count"),
                                skippedSourceCount = rs.getInt("skipped_source_count"),
                                targetStatus = rs.getString("target_status"),
                                targetTableName = rs.getString("target_table_name"),
                                targetRowsLoaded = rs.getLong("target_rows_loaded").takeIf { !rs.wasNull() },
                                errorMessage = rs.getString("error_message"),
                            ),
                            summaryJson = rs.getString("summary_json") ?: "{}",
                            sourceResults = emptyList(),
                            events = emptyList(),
                            artifacts = emptyList(),
                        )
                    }
                }
            }

            requireNotNull(summary) {
                "История запуска '$runId' для DB-модуля '$moduleCode' не найдена."
            }

            val sourceResults = connection.prepareStatement(RunHistorySql.listRunSourceResults(normalizedSchema)).use { stmt ->
                stmt.setString(1, runId)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<DatabaseRunSourceResultResponse>()
                    while (rs.next()) {
                        result += DatabaseRunSourceResultResponse(
                            runSourceResultId = rs.getString("run_source_result_id"),
                            sourceName = rs.getString("source_name"),
                            sortOrder = rs.getInt("sort_order"),
                            status = rs.getString("status"),
                            startedAt = rs.getTimestamp("started_at")?.toInstant(),
                            finishedAt = rs.getTimestamp("finished_at")?.toInstant(),
                            exportedRowCount = rs.getLong("exported_row_count").takeIf { !rs.wasNull() },
                            mergedRowCount = rs.getLong("merged_row_count").takeIf { !rs.wasNull() },
                            errorMessage = rs.getString("error_message"),
                        )
                    }
                    result
                }
            }

            val events = connection.prepareStatement(RunHistorySql.listRunEvents(normalizedSchema)).use { stmt ->
                stmt.setString(1, runId)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<DatabaseRunEventResponse>()
                    while (rs.next()) {
                        result += DatabaseRunEventResponse(
                            runEventId = rs.getString("run_event_id"),
                            seqNo = rs.getInt("seq_no"),
                            createdAt = rs.getTimestamp("created_at").toInstant(),
                            stage = rs.getString("stage"),
                            eventType = rs.getString("event_type"),
                            severity = rs.getString("severity"),
                            sourceName = rs.getString("source_name"),
                            message = rs.getString("message"),
                            payloadJson = readRunPayload(rs.getString("payload_json"), objectMapperWithTime),
                        )
                    }
                    result
                }
            }

            val artifacts = connection.prepareStatement(RunHistorySql.listRunArtifacts(normalizedSchema)).use { stmt ->
                stmt.setString(1, runId)
                stmt.executeQuery().use { rs ->
                    val result = mutableListOf<DatabaseRunArtifactResponse>()
                    while (rs.next()) {
                        result += DatabaseRunArtifactResponse(
                            runArtifactId = rs.getString("run_artifact_id"),
                            artifactKind = rs.getString("artifact_kind"),
                            artifactKey = rs.getString("artifact_key"),
                            filePath = rs.getString("file_path"),
                            storageStatus = rs.getString("storage_status"),
                            fileSizeBytes = rs.getLong("file_size_bytes").takeIf { !rs.wasNull() },
                            contentHash = rs.getString("content_hash"),
                            createdAt = rs.getTimestamp("created_at").toInstant(),
                        )
                    }
                    result
                }
            }

            return summary.copy(
                sourceResults = sourceResults,
                events = events,
                artifacts = artifacts,
            )
        }
    }
}
