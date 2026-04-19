package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.platform.ui.model.CredentialsStatusResponse
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import com.sbrf.lt.platform.ui.model.StartRunRequest
import com.sbrf.lt.platform.ui.module.ModuleRegistry

internal class RunManagerStartSupport(
    private val moduleRegistry: ModuleRegistry,
    private val executionSupport: RunManagerExecutionSupport,
    private val stateSupport: RunManagerStateSupport,
) {
    fun prepareStart(
        request: StartRunRequest,
        snapshots: List<MutableRunSnapshot>,
        credentialProperties: Map<String, String>,
        credentialsStatus: CredentialsStatusResponse,
    ): RunManagerStartContext {
        require(snapshots.none { it.status != ExecutionStatus.SUCCESS && it.status != ExecutionStatus.FAILED }) {
            "Уже выполняется другой запуск. Дождитесь его завершения."
        }
        stateSupport.validateCredentialsBeforeRun(
            configText = request.configText,
            credentialProperties = credentialProperties,
            credentialsStatus = credentialsStatus,
        )

        val module = moduleRegistry.getModule(request.moduleId)
        val snapshot = executionSupport.createSnapshot(module)
        return RunManagerStartContext(
            module = module,
            snapshot = snapshot,
        )
    }
}

internal data class RunManagerStartContext(
    val module: ModuleDescriptor,
    val snapshot: MutableRunSnapshot,
)
