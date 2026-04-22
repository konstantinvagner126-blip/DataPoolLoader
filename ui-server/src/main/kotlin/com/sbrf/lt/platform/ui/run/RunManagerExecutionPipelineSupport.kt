package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.datapool.app.ExecutionListener
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import com.sbrf.lt.platform.ui.model.StartRunRequest
import java.nio.file.Files
import java.nio.file.Path

internal class RunManagerExecutionPipelineSupport(
    private val applicationRunner: ApplicationRunner,
    private val moduleExecutionSource: ModuleExecutionSource,
    private val snapshotSupport: RunManagerSnapshotSupport,
) {
    fun executeRun(
        module: ModuleDescriptor,
        request: StartRunRequest,
        snapshot: MutableRunSnapshot,
        materializeCredentialsFile: (Path) -> Path?,
        publishState: () -> Unit,
    ) {
        runCatching {
            snapshot.status = com.sbrf.lt.datapool.model.ExecutionStatus.RUNNING
            publishState()
            val runtimeSnapshot = moduleExecutionSource.prepareExecution(module, request)
            val tempDir = Files.createTempDirectory("datapool-ui-run-${module.id}-")
            val credentialsPath = materializeCredentialsFile(tempDir)
            val result = applicationRunner.run(
                snapshot = runtimeSnapshot,
                credentialsPath = credentialsPath,
                executionListener = ExecutionListener { event ->
                    snapshotSupport.applyEvent(snapshot, event)
                    publishState()
                },
            )
            snapshotSupport.finalizeSuccess(snapshot, result)
            publishState()
        }.onFailure { ex ->
            snapshotSupport.finalizeFailure(snapshot, ex)
            publishState()
        }
    }
}
