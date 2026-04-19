package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.app.ApplicationRunResult
import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.datapool.app.ExecutionEvent
import com.sbrf.lt.datapool.app.ExecutionListener
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.platform.ui.model.StartRunRequest
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

internal class RunManagerExecutionSupport(
    private val objectMapper: ObjectMapper,
    private val applicationRunner: ApplicationRunner,
    private val moduleExecutionSource: ModuleExecutionSource,
) {
    fun createSnapshot(module: ModuleDescriptor): MutableRunSnapshot =
        MutableRunSnapshot(
            id = java.util.UUID.randomUUID().toString(),
            moduleId = module.id,
            moduleTitle = module.title,
            status = ExecutionStatus.RUNNING,
            startedAt = Instant.now(),
        )

    fun applyEvent(snapshot: MutableRunSnapshot, event: ExecutionEvent) {
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
    }

    fun finalizeSuccess(snapshot: MutableRunSnapshot, result: ApplicationRunResult) {
        snapshot.status = result.status
        snapshot.finishedAt = Instant.now()
        snapshot.outputDir = result.outputDir.toString()
        snapshot.mergedRowCount = result.mergedRowCount
        snapshot.summaryJson = result.summaryFile
            ?.takeIf { java.nio.file.Files.exists(it) }
            ?.let { Files.readString(it) }
    }

    fun finalizeFailure(snapshot: MutableRunSnapshot, ex: Throwable) {
        snapshot.status = ExecutionStatus.FAILED
        snapshot.finishedAt = Instant.now()
        snapshot.errorMessage = ex.message ?: "Неизвестная ошибка"
    }

    fun executeRun(
        module: ModuleDescriptor,
        request: StartRunRequest,
        snapshot: MutableRunSnapshot,
        materializeCredentialsFile: (Path) -> Path?,
        publishState: () -> Unit,
    ) {
        runCatching {
            snapshot.status = ExecutionStatus.RUNNING
            publishState()
            val runtimeSnapshot = moduleExecutionSource.prepareExecution(module, request)
            val tempDir = Files.createTempDirectory("datapool-ui-run-${module.id}-")
            val credentialsPath = materializeCredentialsFile(tempDir)
            val result = applicationRunner.run(
                snapshot = runtimeSnapshot,
                credentialsPath = credentialsPath,
                executionListener = ExecutionListener { event ->
                    applyEvent(snapshot, event)
                    publishState()
                },
            )
            finalizeSuccess(snapshot, result)
            publishState()
        }.onFailure { ex ->
            finalizeFailure(snapshot, ex)
            publishState()
        }
    }

    private fun ExecutionEvent.toUiEventMap(): Map<String, Any?> {
        val result = objectMapper.convertValue(this, MutableMap::class.java)
            .mapKeys { it.key.toString() }
            .toMutableMap()
        result["type"] = javaClass.simpleName
        return result
    }
}
