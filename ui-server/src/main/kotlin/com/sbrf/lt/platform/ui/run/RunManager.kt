package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.platform.ui.config.UiAppConfig
import com.sbrf.lt.platform.ui.config.UiConfigLoader
import com.sbrf.lt.platform.ui.config.storageDirPath
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleDetailsResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupPreviewResponse
import com.sbrf.lt.platform.ui.model.RunHistoryCleanupResultResponse
import com.sbrf.lt.platform.ui.model.StartRunRequest
import com.sbrf.lt.platform.ui.model.UiRunSnapshot
import com.sbrf.lt.platform.ui.model.UiStateResponse
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors

class RunManager(
    private val moduleRegistry: ModuleRegistry = ModuleRegistry(),
    private val applicationRunner: ApplicationRunner = ApplicationRunner(),
    private val uiConfig: UiAppConfig = UiConfigLoader().load(),
    private val stateStore: RunStateStore = RunStateStore(uiConfig.storageDirPath()),
    private val credentialsService: UiCredentialsService = UiCredentialsService(uiConfigProvider = { uiConfig }),
    private val moduleExecutionSource: ModuleExecutionSource = FilesModuleExecutionSource(moduleRegistry),
) : FilesModuleRunOperations, FilesRunHistoryMaintenanceOperations {
    private val executor = Executors.newSingleThreadExecutor()
    private val mapper = com.sbrf.lt.datapool.config.ConfigLoader().objectMapper()
    private val persistenceSupport = RunManagerPersistenceSupport(stateStore)
    private val historyCleanupSupport = RunManagerHistoryCleanupSupport(stateStore, mapper)
    private val executionSupport = RunManagerExecutionSupport(mapper, applicationRunner, moduleExecutionSource)
    private val stateSupport = RunManagerStateSupport(moduleRegistry, uiConfig)
    private val startSupport = RunManagerStartSupport(moduleRegistry, executionSupport, stateSupport)
    private val runtimeStateSupport = RunManagerRuntimeStateSupport(
        persistenceSupport = persistenceSupport,
        stateSupport = stateSupport,
        credentialsProvider = credentialsService,
    )

    init {
        runtimeStateSupport.emitCurrentState()
    }

    override fun updates() = runtimeStateSupport.updates()

    @Synchronized
    override fun currentState(): UiStateResponse = runtimeStateSupport.currentState()

    @Synchronized
    override fun uploadCredentials(fileName: String, content: String): CredentialsStatusResponse {
        credentialsService.uploadCredentials(fileName, content)
        publishState()
        return currentCredentialsStatus()
    }

    @Synchronized
    override fun currentCredentialsStatus(): CredentialsStatusResponse = credentialsService.currentCredentialsStatus()

    @Synchronized
    override fun startRun(request: StartRunRequest): UiRunSnapshot {
        val startContext = startSupport.prepareStart(
            request = request,
            snapshots = runtimeStateSupport.snapshots(),
            credentialProperties = currentProperties(),
            credentialsStatus = currentCredentialsStatus(),
        )
        val module = startContext.module
        val snapshot = startContext.snapshot
        runtimeStateSupport.prependSnapshot(snapshot)
        publishState()

        executor.submit {
            executionSupport.executeRun(
                module = module,
                request = request,
                snapshot = snapshot,
                materializeCredentialsFile = ::materializeCredentialsFile,
                publishState = ::publishState,
            )
        }

        return snapshot.toUi()
    }

    @Synchronized
    override fun loadModuleDetails(moduleId: String): ModuleDetailsResponse =
        stateSupport.loadModuleDetails(
            moduleId = moduleId,
            credentialsStatus = currentCredentialsStatus(),
            credentialProperties = currentProperties(),
        )

    override fun materializeCredentialsFile(tempDir: Path): Path? {
        return credentialsService.materializeCredentialsFile(tempDir)
    }

    override fun currentProperties(): Map<String, String> = credentialsService.currentProperties()

    @Synchronized
    private fun publishState() {
        runtimeStateSupport.publishState()
    }

    @Synchronized
    override fun previewHistoryCleanup(
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): RunHistoryCleanupPreviewResponse =
        historyCleanupSupport.previewHistoryCleanup(
            snapshots = runtimeStateSupport.snapshots(),
            cutoffTimestamp = cutoffTimestamp,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
        )

    @Synchronized
    override fun executeHistoryCleanup(
        cutoffTimestamp: Instant,
        retentionDays: Int,
        keepMinRunsPerModule: Int,
        disableSafeguard: Boolean,
    ): RunHistoryCleanupResultResponse {
        val result = historyCleanupSupport.executeHistoryCleanup(
            snapshots = runtimeStateSupport.snapshots(),
            cutoffTimestamp = cutoffTimestamp,
            retentionDays = retentionDays,
            keepMinRunsPerModule = keepMinRunsPerModule,
            disableSafeguard = disableSafeguard,
        )
        if (result.deleted) {
            publishState()
        }
        return result.response
    }
}
