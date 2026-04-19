package com.sbrf.lt.platform.ui.run.history

import com.sbrf.lt.platform.ui.model.ModuleRunDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleRunHistoryResponse
import com.sbrf.lt.platform.ui.model.ModuleRunPageSessionResponse
import com.sbrf.lt.platform.ui.model.ModuleRunSummaryResponse
import com.sbrf.lt.platform.ui.model.UiRunSnapshot
import com.sbrf.lt.platform.ui.error.UiEntityNotFoundException
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import com.sbrf.lt.platform.ui.module.backend.ModuleActor
import com.sbrf.lt.platform.ui.run.FilesModuleRunOperations

/**
 * FILES-реализация общего backend-контракта истории запусков.
 */
class FilesModuleRunHistoryService(
    private val moduleRegistry: ModuleRegistry,
    private val runManager: FilesModuleRunOperations,
) : ModuleRunHistoryService {
    override fun loadSession(moduleId: String, actor: ModuleActor?): ModuleRunPageSessionResponse {
        val module = moduleRegistry.loadModuleDetails(moduleId)
        return ModuleRunPageSessionResponse(
            storageMode = "FILES",
            moduleId = module.id,
            moduleTitle = module.descriptor.title,
            moduleMeta = "${module.configPath} · режим хранения: Файлы",
        )
    }

    override fun listRuns(moduleId: String, actor: ModuleActor?, limit: Int): ModuleRunHistoryResponse {
        val state = runManager.currentState()
        return ModuleRunHistoryResponse(
            storageMode = "FILES",
            moduleId = moduleId,
            activeRunId = state.activeRun?.takeIf { it.moduleId == moduleId }?.id,
            uiSettings = state.uiSettings,
            runs = state.history
                .filter { it.moduleId == moduleId }
                .take(limit)
                .map { it.toCommonSummary() },
        )
    }

    override fun loadRunDetails(moduleId: String, runId: String, actor: ModuleActor?): ModuleRunDetailsResponse {
        val run = findRun(moduleId, runId)
        val sourceResults = projectFilesRunSourceResults(run)
        return ModuleRunDetailsResponse(
            run = run.toCommonSummary(),
            summaryJson = run.summaryJson,
            sourceResults = sourceResults,
            events = projectFilesRunEvents(run),
            artifacts = projectFilesRunArtifacts(run, sourceResults),
        )
    }

    private fun findRun(moduleId: String, runId: String): UiRunSnapshot =
        runManager.currentState().history
            .firstOrNull { it.moduleId == moduleId && it.id == runId }
            ?: throw UiEntityNotFoundException("Запуск '$runId' для модуля '$moduleId' не найден.")
}

private fun UiRunSnapshot.toCommonSummary(): ModuleRunSummaryResponse {
    val sourceResults = projectFilesRunSourceResults(this)
    val targetState = projectFilesTargetState(this)
    return ModuleRunSummaryResponse(
        runId = id,
        moduleId = moduleId,
        moduleTitle = moduleTitle,
        status = status.name,
        startedAt = startedAt,
        finishedAt = finishedAt,
        requestedAt = startedAt,
        outputDir = outputDir,
        mergedRowCount = mergedRowCount,
        errorMessage = errorMessage,
        successfulSourceCount = sourceResults.count { it.status == "SUCCESS" },
        failedSourceCount = sourceResults.count { it.status == "FAILED" },
        skippedSourceCount = sourceResults.count { it.status == "SKIPPED" },
        targetStatus = targetState.status,
        targetTableName = targetState.tableName,
        targetRowsLoaded = targetState.rowsLoaded,
    )
}
