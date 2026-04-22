package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.platform.ui.model.StartRunRequest
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import java.nio.file.Path

internal class RunManagerExecutionSupport(
    objectMapper: ObjectMapper,
    applicationRunner: ApplicationRunner,
    moduleExecutionSource: ModuleExecutionSource,
) {
    private val snapshotSupport = RunManagerSnapshotSupport(objectMapper)
    private val pipelineSupport = RunManagerExecutionPipelineSupport(
        applicationRunner = applicationRunner,
        moduleExecutionSource = moduleExecutionSource,
        snapshotSupport = snapshotSupport,
    )

    fun createSnapshot(module: ModuleDescriptor): MutableRunSnapshot =
        snapshotSupport.createSnapshot(module)

    fun executeRun(
        module: ModuleDescriptor,
        request: StartRunRequest,
        snapshot: MutableRunSnapshot,
        materializeCredentialsFile: (Path) -> Path?,
        publishState: () -> Unit,
    ) = pipelineSupport.executeRun(
        module = module,
        request = request,
        snapshot = snapshot,
        materializeCredentialsFile = materializeCredentialsFile,
        publishState = publishState,
    )
}
