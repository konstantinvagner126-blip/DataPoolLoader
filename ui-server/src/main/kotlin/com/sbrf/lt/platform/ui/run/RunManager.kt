package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.ApplicationRunResult
import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.datapool.app.ExecutionEvent
import com.sbrf.lt.datapool.app.ExecutionListener
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import com.sbrf.lt.platform.ui.config.storageDirPath
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.CurrentStorageModuleResponse
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupModuleResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupResultResponse
import com.sbrf.lt.platform.ui.model.StartRunRequest
import com.sbrf.lt.platform.ui.model.UiSettingsResponse
import com.sbrf.lt.platform.ui.model.UiRunSnapshot
import com.sbrf.lt.platform.ui.model.UiStateResponse
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.io.path.readText

class RunManager(
    private val moduleRegistry: ModuleRegistry = ModuleRegistry(),
    private val applicationRunner: ApplicationRunner = ApplicationRunner(),
    private val uiConfig: UiAppConfig = UiConfigLoader().load(),
    private val stateStore: RunStateStore = RunStateStore(uiConfig.storageDirPath()),
    private val credentialsService: UiCredentialsService = UiCredentialsService(uiConfigProvider = { uiConfig }),
    private val moduleExecutionSource: ModuleExecutionSource = FilesModuleExecutionSource(moduleRegistry),
) : UiCredentialsProvider {
    private val executor = Executors.newSingleThreadExecutor()
    private val snapshots = mutableListOf<MutableRunSnapshot>()
    private val updatesFlow = MutableSharedFlow<UiStateResponse>(replay = 1, extraBufferCapacity = 32)
    private val mapper = com.sbrf.lt.datapool.config.ConfigLoader().objectMapper()

    init {
        restorePersistedState()
        updatesFlow.tryEmit(currentState())
    }

    fun updates() = updatesFlow.asSharedFlow()

    @Synchronized
    fun currentState(): UiStateResponse {
        val ordered = snapshots.sortedByDescending { it.startedAt }
        return UiStateResponse(
            credentialsStatus = currentCredentialsStatus(),
            uiSettings = UiSettingsResponse(
                showTechnicalDiagnostics = uiConfig.showTechnicalDiagnostics,
                showRawSummaryJson = uiConfig.showRawSummaryJson,
            ),
            activeRun = ordered.firstOrNull { it.status != ExecutionStatus.SUCCESS && it.status != ExecutionStatus.FAILED }?.toUi(),
            history = ordered.map { it.toUi() },
        )
    }

    @Synchronized
    fun uploadCredentials(fileName: String, content: String): CredentialsStatusResponse {
        credentialsService.uploadCredentials(fileName, content)
        publishState()
        return currentCredentialsStatus()
    }

    @Synchronized
    override fun currentCredentialsStatus(): CredentialsStatusResponse = credentialsService.currentCredentialsStatus()

    @Synchronized
    fun startRun(request: StartRunRequest): UiRunSnapshot {
        require(snapshots.none { it.status != ExecutionStatus.SUCCESS && it.status != ExecutionStatus.FAILED }) {
            "Уже выполняется другой запуск. Дождитесь его завершения."
        }
        validateCredentialsBeforeRun(request.configText)

        val module = moduleRegistry.getModule(request.moduleId)
        val snapshot = MutableRunSnapshot(
            id = UUID.randomUUID().toString(),
            moduleId = module.id,
            moduleTitle = module.title,
            status = ExecutionStatus.RUNNING,
            startedAt = Instant.now(),
        )
        snapshots.add(0, snapshot)
        publishState()

        executor.submit {
            runCatching {
                snapshot.status = ExecutionStatus.RUNNING
                publishState()
                val runResult = runModule(module, request, snapshot)
                finalizeSuccess(snapshot, runResult)
            }.onFailure { ex ->
                finalizeFailure(snapshot, ex)
            }
        }

        return snapshot.toUi()
    }

    private fun runModule(
        module: ModuleDescriptor,
        request: StartRunRequest,
        snapshot: MutableRunSnapshot,
    ): ApplicationRunResult {
        snapshot.status = ExecutionStatus.RUNNING
        publishState()
        val runtimeSnapshot = moduleExecutionSource.prepareExecution(module, request)
        val tempDir = Files.createTempDirectory("datapool-ui-run-${module.id}-")
        val credentialsPath = materializeCredentialsFile(tempDir)
        return applicationRunner.run(
            snapshot = runtimeSnapshot,
            credentialsPath = credentialsPath,
            executionListener = ExecutionListener { event ->
                handleEvent(snapshot, event)
            },
        )
    }

    @Synchronized
    fun loadModuleDetails(moduleId: String): ModuleDetailsResponse {
        val details = moduleRegistry.loadModuleDetails(moduleId)
        val requirement = analyzeCredentialRequirements(details.configText, currentProperties())
        return details.copy(
            requiresCredentials = requirement.requiresCredentials,
            credentialsStatus = currentCredentialsStatus(),
            requiredCredentialKeys = requirement.requiredKeys,
            missingCredentialKeys = requirement.missingKeys,
            credentialsReady = !requirement.requiresCredentials || requirement.ready,
        )
    }

    private fun validateCredentialsBeforeRun(configText: String) {
        val requirement = analyzeCredentialRequirements(configText, currentProperties())
        if (!requirement.requiresCredentials) {
            return
        }
        val status = currentCredentialsStatus()
        require(requirement.ready) {
            buildMissingCredentialValuesMessage(
                subjectLabel = "модуля",
                missingKeys = requirement.missingKeys,
                credentialsStatus = status,
            )
        }
    }

    override fun materializeCredentialsFile(tempDir: Path): Path? {
        return credentialsService.materializeCredentialsFile(tempDir)
    }

    override fun currentProperties(): Map<String, String> = credentialsService.currentProperties()

    private fun usesCredentialPlaceholders(configText: String): Boolean =
        containsCredentialPlaceholders(configText)

    @Synchronized
    private fun handleEvent(snapshot: MutableRunSnapshot, event: ExecutionEvent) {
        snapshot.events.add(event.toUiEventMap())
        when (event) {
            is com.sbrf.lt.datapool.app.RunStartedEvent -> snapshot.status = ExecutionStatus.RUNNING
            is com.sbrf.lt.datapool.app.SourceExportProgressEvent -> snapshot.sourceProgress[event.sourceName] = event.rowCount
            is com.sbrf.lt.datapool.app.RunFinishedEvent -> {
                snapshot.status = event.status
                snapshot.finishedAt = event.timestamp
                snapshot.outputDir = event.outputDir
                snapshot.mergedRowCount = event.mergedRowCount
                snapshot.errorMessage = event.errorMessage
            }
            is com.sbrf.lt.datapool.app.MergeFinishedEvent -> snapshot.mergedRowCount = event.rowCount
            else -> Unit
        }
        publishState()
    }

    @Synchronized
    private fun finalizeSuccess(snapshot: MutableRunSnapshot, result: ApplicationRunResult) {
        snapshot.status = result.status
        snapshot.finishedAt = Instant.now()
        snapshot.outputDir = result.outputDir.toString()
        snapshot.mergedRowCount = result.mergedRowCount
        snapshot.summaryJson = result.summaryFile?.takeIf { Files.exists(it) }?.readText()
        publishState()
    }

    @Synchronized
    private fun finalizeFailure(snapshot: MutableRunSnapshot, ex: Throwable) {
        snapshot.status = ExecutionStatus.FAILED
        snapshot.finishedAt = Instant.now()
        snapshot.errorMessage = ex.message ?: "Неизвестная ошибка"
        publishState()
    }

    @Synchronized
    private fun publishState() {
        persistState()
        updatesFlow.tryEmit(currentState())
    }

    @Synchronized
    private fun restorePersistedState() {
        val persisted = stateStore.load()
        snapshots.clear()
        snapshots.addAll(
            persisted.history.map { snapshot ->
                MutableRunSnapshot(
                    id = snapshot.id,
                    moduleId = snapshot.moduleId,
                    moduleTitle = snapshot.moduleTitle,
                    status = snapshot.status,
                    startedAt = snapshot.startedAt,
                    finishedAt = snapshot.finishedAt,
                    outputDir = snapshot.outputDir,
                    mergedRowCount = snapshot.mergedRowCount,
                    summaryJson = snapshot.summaryJson,
                    errorMessage = snapshot.errorMessage,
                    sourceProgress = snapshot.sourceProgress.toMutableMap(),
                    events = snapshot.events.toMutableList(),
                )
            },
        )
    }

    @Synchronized
    fun previewHistoryCleanup(
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): RunHistoryCleanupPreviewResponse {
        val preview = buildHistoryCleanupPreview(
            cutoffTimestamp = cutoffTimestamp,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
        )
        return RunHistoryCleanupPreviewResponse(
            storageMode = "FILES",
            safeguardEnabled = !disableSafeguard,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            cutoffTimestamp = cutoffTimestamp,
            currentRunsCount = snapshots.size,
            currentModulesCount = snapshots.map { it.moduleId }.distinct().size,
            currentStorageBytes = stateStore.currentFileSizeBytes(),
            currentOldestRequestedAt = snapshots.minOfOrNull { it.startedAt },
            currentNewestRequestedAt = snapshots.maxOfOrNull { it.startedAt },
            currentTopModules = buildCurrentHistoryUsageModules(),
            estimatedBytesToFree = estimateHistoryCleanupBytesToFree(preview.runIds),
            totalModulesAffected = preview.modules.size,
            totalRunsToDelete = preview.runIds.size,
            totalEventsToDelete = preview.totalEventsToDelete,
            modules = preview.modules,
        )
    }

    @Synchronized
    fun executeHistoryCleanup(
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): RunHistoryCleanupResultResponse {
        val preview = buildHistoryCleanupPreview(
            cutoffTimestamp = cutoffTimestamp,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
        )
        if (preview.runIds.isNotEmpty()) {
            snapshots.removeAll { snapshot -> snapshot.id in preview.runIds }
            publishState()
        }
        return RunHistoryCleanupResultResponse(
            storageMode = "FILES",
            safeguardEnabled = !disableSafeguard,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            cutoffTimestamp = cutoffTimestamp,
            finishedAt = Instant.now(),
            totalModulesAffected = preview.modules.size,
            totalRunsDeleted = preview.runIds.size,
            totalEventsDeleted = preview.totalEventsToDelete,
            modules = preview.modules,
        )
    }

    @Synchronized
    private fun persistState() {
        stateStore.save(
            PersistedRunState(
                history = snapshots
                    .sortedByDescending { it.startedAt }
                    .map { it.toUi() },
            ),
        )
    }

    private fun ExecutionEvent.toUiEventMap(): Map<String, Any?> {
        val result = mapper.convertValue(this, MutableMap::class.java)
            .mapKeys { it.key.toString() }
            .toMutableMap()
        result["type"] = javaClass.simpleName
        return result
    }

    private fun buildHistoryCleanupPreview(
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): FilesHistoryCleanupPreview {
        val deletableByModule = snapshots
            .sortedByDescending { it.startedAt }
            .groupBy { it.moduleId }
            .mapNotNull { (moduleId, moduleSnapshots) ->
                val runsToDelete = moduleSnapshots.filterIndexed { index, snapshot ->
                    val olderThanCutoff = snapshot.startedAt.isBefore(cutoffTimestamp)
                    val canDeleteBySafeguard = disableSafeguard || index >= keepMinRunsPerModule
                    snapshot.status != ExecutionStatus.RUNNING &&
                        olderThanCutoff &&
                        canDeleteBySafeguard
                }
                if (runsToDelete.isEmpty()) {
                    null
                } else {
                    FilesHistoryCleanupModulePreview(
                        moduleCode = moduleId,
                        runIds = runsToDelete.map { it.id },
                        totalEventsToDelete = runsToDelete.sumOf { it.events.size },
                        summary = RunHistoryCleanupModuleResponse(
                            moduleCode = moduleId,
                            totalRunsToDelete = runsToDelete.size,
                            oldestRequestedAt = runsToDelete.minOfOrNull { it.startedAt },
                            newestRequestedAt = runsToDelete.maxOfOrNull { it.startedAt },
                        ),
                    )
                }
            }

        return FilesHistoryCleanupPreview(
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            runIds = deletableByModule.flatMapTo(linkedSetOf()) { it.runIds },
            totalEventsToDelete = deletableByModule.sumOf { it.totalEventsToDelete },
            modules = deletableByModule.map { it.summary }.sortedBy { it.moduleCode },
        )
    }

    private fun estimateHistoryCleanupBytesToFree(runIdsToDelete: Set<String>): Long? {
        val currentSize = stateStore.currentFileSizeBytes()
        if (currentSize <= 0L || runIdsToDelete.isEmpty()) {
            return 0L
        }
        val projectedState = PersistedRunState(
            history = snapshots
                .filterNot { it.id in runIdsToDelete }
                .sortedByDescending { it.startedAt }
                .map { it.toUi() },
        )
        val projectedBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(projectedState).size.toLong()
        return (currentSize - projectedBytes).coerceAtLeast(0L)
    }

    private fun buildCurrentHistoryUsageModules(): List<CurrentStorageModuleResponse> =
        snapshots
            .groupBy { it.moduleId }
            .map { (moduleCode, moduleSnapshots) ->
                CurrentStorageModuleResponse(
                    moduleCode = moduleCode,
                    currentRunsCount = moduleSnapshots.size,
                    currentStorageBytes = estimateHistoryStorageBytesForSnapshots(moduleSnapshots),
                    oldestRequestedAt = moduleSnapshots.minOfOrNull { it.startedAt },
                    newestRequestedAt = moduleSnapshots.maxOfOrNull { it.startedAt },
                )
            }
            .sortedWith(
                compareByDescending<CurrentStorageModuleResponse> { it.currentStorageBytes }
                    .thenByDescending { it.currentRunsCount }
                    .thenBy { it.moduleCode },
            )
            .take(5)

    private fun estimateHistoryStorageBytesForSnapshots(moduleSnapshots: List<MutableRunSnapshot>): Long =
        mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(
            PersistedRunState(
                history = moduleSnapshots
                    .sortedByDescending { it.startedAt }
                    .map { it.toUi() },
            ),
        ).size.toLong()

    private data class FilesHistoryCleanupPreview(
        val retentionDays: Int,
        val keepMinRunsPerModule: Int,
        val runIds: Set<String>,
        val totalEventsToDelete: Int,
        val modules: List<RunHistoryCleanupModuleResponse>,
    )

    private data class FilesHistoryCleanupModulePreview(
        val moduleCode: String,
        val runIds: List<String>,
        val totalEventsToDelete: Int,
        val summary: RunHistoryCleanupModuleResponse,
    )
}
