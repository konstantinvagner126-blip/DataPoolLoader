package com.sbrf.lt.datapool.ui

import com.sbrf.lt.datapool.app.ApplicationRunResult
import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.datapool.app.ExecutionEvent
import com.sbrf.lt.datapool.app.ExecutionListener
import com.sbrf.lt.datapool.config.ConfigLoader
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class RunManager(
    private val moduleRegistry: ModuleRegistry = ModuleRegistry(),
    private val applicationRunner: ApplicationRunner = ApplicationRunner(),
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val snapshots = mutableListOf<MutableRunSnapshot>()
    private val updatesFlow = MutableSharedFlow<UiStateResponse>(replay = 1, extraBufferCapacity = 32)
    private val configLoader = ConfigLoader()

    init {
        updatesFlow.tryEmit(currentState())
    }

    fun updates() = updatesFlow.asSharedFlow()

    @Synchronized
    fun currentState(): UiStateResponse {
        val ordered = snapshots.sortedByDescending { it.startedAt }
        return UiStateResponse(
            activeRun = ordered.firstOrNull { it.status != ExecutionStatus.SUCCESS && it.status != ExecutionStatus.FAILED }?.toUi(),
            history = ordered.map { it.toUi() },
        )
    }

    @Synchronized
    fun startRun(request: StartRunRequest): UiRunSnapshot {
        require(snapshots.none { it.status != ExecutionStatus.SUCCESS && it.status != ExecutionStatus.FAILED }) {
            "Уже выполняется другой запуск. Дождитесь его завершения."
        }

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
        val tempDir = Files.createTempDirectory("datapool-ui-${module.id}-")
        val tempConfig = prepareWorkingCopy(module, request, tempDir)
        val credentialsPath = System.getProperty("credentials.file")?.let(Path::of)
        return applicationRunner.run(
            configPath = tempConfig,
            credentialsPath = credentialsPath,
            executionListener = ExecutionListener { event ->
                handleEvent(snapshot, event)
            },
        )
    }

    private fun prepareWorkingCopy(
        module: ModuleDescriptor,
        request: StartRunRequest,
        tempDir: Path,
    ): Path {
        val root = configLoader.objectMapper().readTree(request.configText) as ObjectNode
        val appNode = root.path("app") as ObjectNode

        appNode.path("commonSqlFile").takeIf { it.isTextual }.also { node ->
            if (node != null) {
                rewriteSqlReference(module, tempDir, request, node.asText(), appNode, "commonSqlFile")
            }
        }

        appNode.path("sources").takeIf { it.isArray }?.forEach { sourceNode ->
            sourceNode.path("sqlFile").takeIf { it.isTextual }?.also { node ->
                rewriteSqlReference(module, tempDir, request, node.asText(), sourceNode as ObjectNode, "sqlFile")
            }
        }

        val configPath = tempDir.resolve("application.yml")
        configPath.writeText(configLoader.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root))
        return configPath
    }

    private fun rewriteSqlReference(
        module: ModuleDescriptor,
        tempDir: Path,
        request: StartRunRequest,
        originalRef: String,
        objectNode: ObjectNode,
        fieldName: String,
    ) {
        val content = request.sqlFiles[originalRef]
            ?: moduleRegistry.resolveSqlPath(module, originalRef)?.takeIf { Files.exists(it) }?.readText()
            ?: return

        val relativePath = when {
            originalRef.startsWith("classpath:") -> originalRef.removePrefix("classpath:").removePrefix("/")
            else -> originalRef.removePrefix("./")
        }
        val target = tempDir.resolve(relativePath).normalize()
        target.parent?.createDirectories()
        target.writeText(content)

        objectNode.put(fieldName, relativePath)
    }

    @Synchronized
    private fun handleEvent(snapshot: MutableRunSnapshot, event: ExecutionEvent) {
        snapshot.events.add(event)
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
        updatesFlow.tryEmit(currentState())
    }

    private data class MutableRunSnapshot(
        val id: String,
        val moduleId: String,
        val moduleTitle: String,
        var status: ExecutionStatus,
        val startedAt: Instant,
        var finishedAt: Instant? = null,
        var outputDir: String? = null,
        var mergedRowCount: Long = 0,
        var summaryJson: String? = null,
        var errorMessage: String? = null,
        val sourceProgress: MutableMap<String, Long> = linkedMapOf(),
        val events: MutableList<ExecutionEvent> = mutableListOf(),
    ) {
        fun toUi(): UiRunSnapshot = UiRunSnapshot(
            id = id,
            moduleId = moduleId,
            moduleTitle = moduleTitle,
            status = status,
            startedAt = startedAt,
            finishedAt = finishedAt,
            outputDir = outputDir,
            mergedRowCount = mergedRowCount,
            summaryJson = summaryJson,
            errorMessage = errorMessage,
            sourceProgress = sourceProgress.toMap(),
            events = events.toList(),
        )
    }
}
