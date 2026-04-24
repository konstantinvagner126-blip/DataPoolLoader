package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.db.registry.model.RegistryModuleDraft
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunSummaryResponse
import com.sbrf.lt.platform.ui.model.ModuleCatalogItemResponse
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleMetadataDescriptorResponse
import com.sbrf.lt.platform.ui.model.SaveModuleRequest
import com.sbrf.lt.platform.ui.module.DatabaseEditableModule
import com.sbrf.lt.platform.ui.module.DatabaseModuleRegistryOperations
import java.nio.file.Path
import java.time.Instant

internal class InMemoryDatabaseRunStore(
    private val runsByModule: MutableMap<String, MutableList<DatabaseModuleRunSummaryResponse>>,
) : DatabaseRunExecutionStore, DatabaseRunQueryStore {
    val markedFailedRunIds = mutableListOf<String>()

    override fun activeModuleCodes(): Set<String> =
        runsByModule.entries
            .filter { (_, runs) -> runs.any { it.status == "RUNNING" } }
            .mapTo(linkedSetOf()) { it.key }

    override fun listRuns(moduleCode: String, limit: Int): List<DatabaseModuleRunSummaryResponse> =
        runsByModule[moduleCode].orEmpty().take(limit)

    override fun loadRunDetails(moduleCode: String, runId: String): DatabaseModuleRunDetailsResponse =
        DatabaseModuleRunDetailsResponse(
            run = listRuns(moduleCode, 100).first { it.runId == runId },
            summaryJson = "{}",
            sourceResults = emptyList(),
            events = emptyList(),
            artifacts = emptyList(),
        )

    override fun hasActiveRun(moduleCode: String): Boolean =
        activeRunIds(moduleCode).isNotEmpty()

    override fun activeRunIds(moduleCode: String): List<String> =
        runsByModule[moduleCode].orEmpty()
            .filter { it.status == "RUNNING" }
            .map { it.runId }

    override fun markRunFailed(runId: String, finishedAt: Instant, errorMessage: String) {
        runsByModule.forEach { (_, runs) ->
            val index = runs.indexOfFirst { it.runId == runId }
            if (index >= 0) {
                val current = runs[index]
                runs[index] = current.copy(
                    status = "FAILED",
                    finishedAt = finishedAt,
                    errorMessage = errorMessage,
                )
                markedFailedRunIds += runId
                return
            }
        }
    }

    override fun createRun(context: DatabaseModuleRunContext, startedAt: Instant, outputDir: String) = unsupported()
    override fun markSourceStarted(runId: String, sourceName: String, startedAt: Instant) = unsupported()
    override fun updateSourceProgress(runId: String, sourceName: String, timestamp: Instant, exportedRowCount: Long) = unsupported()
    override fun markSourceFinished(
        runId: String,
        sourceName: String,
        status: String,
        finishedAt: Instant,
        exportedRowCount: Long?,
        errorMessage: String?,
    ) = unsupported()

    override fun markSourceSkipped(runId: String, sourceName: String, finishedAt: Instant, message: String) = unsupported()
    override fun updateSourceMergedRows(runId: String, sourceName: String, mergedRowCount: Long) = unsupported()
    override fun updateMergedRowCount(runId: String, mergedRowCount: Long) = unsupported()
    override fun updateTargetStatus(runId: String, targetStatus: String, targetTableName: String?, targetRowsLoaded: Long?) = unsupported()
    override fun appendEvent(
        runId: String,
        seqNo: Int,
        stage: String,
        eventType: String,
        severity: String,
        sourceName: String?,
        message: String,
        payload: Map<String, Any?>,
    ) = unsupported()

    override fun upsertArtifact(
        runId: String,
        artifactKind: String,
        artifactKey: String,
        filePath: String,
        storageStatus: String,
        fileSizeBytes: Long?,
        contentHash: String?,
    ) = unsupported()

    override fun markArtifactDeleted(runId: String, artifactKind: String, artifactKey: String) = unsupported()

    override fun finishRun(
        runId: String,
        finishedAt: Instant,
        status: String,
        mergedRowCount: Long?,
        successfulSourceCount: Int,
        failedSourceCount: Int,
        skippedSourceCount: Int,
        targetStatus: String,
        targetTableName: String?,
        targetRowsLoaded: Long?,
        summaryJson: String,
        errorMessage: String?,
    ) = unsupported()

    override fun fileSize(filePath: Path): Long? = unsupported()

    private fun unsupported(): Nothing = error("not used in this test")
}

internal class StubDatabaseModuleRegistryOperations(
    private val moduleCode: String = "db-demo",
    private val moduleTitle: String = "DB Demo",
) : DatabaseModuleRegistryOperations {
    override fun listModules(includeHidden: Boolean): List<ModuleCatalogItemResponse> = error("not used in this test")

    override fun loadModuleDetails(
        moduleCode: String,
        actorId: String,
        actorSource: String,
    ): DatabaseEditableModule =
        sampleEditableModule(
            moduleCode = moduleCode,
            moduleTitle = if (moduleCode == this.moduleCode) moduleTitle else moduleCode,
        )

    override fun saveWorkingCopy(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        request: SaveModuleRequest,
    ) = error("not used in this test")

    override fun discardWorkingCopy(moduleCode: String, actorId: String, actorSource: String) = error("not used in this test")
    override fun publishWorkingCopy(moduleCode: String, actorId: String, actorSource: String, actorDisplayName: String?) = error("not used in this test")
    override fun createModule(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
        originKind: String,
        request: RegistryModuleDraft,
    ) = error("not used in this test")

    override fun deleteModule(moduleCode: String, actorId: String) = error("not used in this test")
}

internal class StubUiCredentialsProvider : UiCredentialsProvider {
    override fun currentCredentialsStatus(): CredentialsStatusResponse =
        CredentialsStatusResponse(
            mode = "NOT_FOUND",
            displayName = "credential.properties не найден",
            fileAvailable = false,
            uploaded = false,
        )

    override fun materializeCredentialsFile(tempDir: Path): Path? = null
}

internal fun sampleRunSummary(
    runId: String,
    status: String,
    requestedAt: Instant = Instant.parse("2026-04-24T00:00:00Z"),
    moduleCode: String = "db-demo",
    moduleTitle: String = "DB Demo",
): DatabaseModuleRunSummaryResponse =
    DatabaseModuleRunSummaryResponse(
        runId = runId,
        executionSnapshotId = "snapshot-$runId",
        status = status,
        launchSourceKind = "WORKING_COPY",
        requestedAt = requestedAt,
        startedAt = requestedAt.plusSeconds(1),
        finishedAt = null,
        moduleCode = moduleCode,
        moduleTitle = moduleTitle,
        outputDir = "/tmp/$runId",
        mergedRowCount = null,
        successfulSourceCount = 0,
        failedSourceCount = 0,
        skippedSourceCount = 0,
        targetStatus = "PENDING",
        targetTableName = "public.demo_target",
        targetRowsLoaded = null,
        errorMessage = null,
    )

internal fun sampleEditableModule(
    moduleCode: String = "db-demo",
    moduleTitle: String = "DB Demo",
): DatabaseEditableModule =
    DatabaseEditableModule(
        module = ModuleDetailsResponse(
            id = moduleCode,
            descriptor = ModuleMetadataDescriptorResponse(
                title = moduleTitle,
            ),
            configPath = "db:$moduleCode",
            configText = "app:\n  sources: []",
            sqlFiles = emptyList(),
            requiresCredentials = false,
            credentialsStatus = CredentialsStatusResponse(
                mode = "NOT_FOUND",
                displayName = "credential.properties не найден",
                fileAvailable = false,
                uploaded = false,
            ),
        ),
        sourceKind = "CURRENT_REVISION",
        currentRevisionId = "revision-$moduleCode",
    )
