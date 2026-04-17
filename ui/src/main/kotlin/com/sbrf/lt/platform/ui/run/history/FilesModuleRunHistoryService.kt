package com.sbrf.lt.platform.ui.run.history

import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.platform.ui.model.ModuleRunDetailsResponse
import com.sbrf.lt.platform.ui.model.ModuleRunHistoryResponse
import com.sbrf.lt.platform.ui.model.ModuleRunPageSessionResponse
import com.sbrf.lt.platform.ui.model.ModuleRunSummaryResponse
import com.sbrf.lt.platform.ui.model.UiRunSnapshot
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import com.sbrf.lt.platform.ui.module.backend.ModuleActor
import com.sbrf.lt.platform.ui.run.RunManager

/**
 * FILES-реализация общего backend-контракта истории запусков.
 */
class FilesModuleRunHistoryService(
    private val moduleRegistry: ModuleRegistry,
    private val runManager: RunManager,
) : ModuleRunHistoryService {
    override fun loadSession(moduleId: String, actor: ModuleActor?): ModuleRunPageSessionResponse {
        val module = moduleRegistry.loadModuleDetails(moduleId)
        return ModuleRunPageSessionResponse(
            storageMode = "FILES",
            moduleId = module.id,
            moduleTitle = module.title,
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
            ?: error("Запуск '$runId' для модуля '$moduleId' не найден.")
}

private fun UiRunSnapshot.toCommonSummary(): ModuleRunSummaryResponse {
    val sourceResults = projectFilesRunSourceResults(this)
    val targetStatus = detectFilesTargetStatus(events, status)
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
        targetStatus = targetStatus,
    )
}

private fun detectFilesTargetStatus(events: List<Map<String, Any?>>, runStatus: ExecutionStatus): String =
    events.asReversed()
        .firstNotNullOfOrNull { event ->
            when (detectFilesEventType(event)) {
                "TargetImportFinishedEvent" -> event.statusValue()
                else -> null
            }
        }
        ?: if (runStatus == ExecutionStatus.FAILED) "FAILED" else "NOT_ENABLED"
