package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.RuntimeModuleSnapshot
import com.sbrf.lt.platform.ui.model.ModuleDescriptor
import com.sbrf.lt.platform.ui.model.StartRunRequest
import com.sbrf.lt.platform.ui.module.ModuleRegistry
import java.nio.file.Files
import kotlin.io.path.readText

/**
 * Подготавливает runtime snapshot для файлового модуля из `apps`.
 */
class FilesModuleExecutionSource(
    private val moduleRegistry: ModuleRegistry,
    private val snapshotFactory: RuntimeConfigSnapshotFactory = RuntimeConfigSnapshotFactory(),
) : ModuleExecutionSource {

    override fun prepareExecution(
        module: ModuleDescriptor,
        request: StartRunRequest,
    ): RuntimeModuleSnapshot =
        snapshotFactory.createSnapshot(
            moduleCode = module.id,
            moduleTitle = module.title,
            configText = request.configText,
            sqlFiles = request.sqlFiles,
            launchSourceKind = "FILES",
            configLocation = module.configFile.toString(),
        ) { ref ->
            moduleRegistry.resolveSqlPath(module, ref)?.takeIf { Files.exists(it) }?.readText()
        }
}
