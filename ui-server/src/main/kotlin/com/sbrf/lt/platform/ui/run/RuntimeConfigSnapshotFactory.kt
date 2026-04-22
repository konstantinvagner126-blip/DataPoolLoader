package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.RuntimeModuleSnapshot
import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Files

/**
 * Создает runtime snapshot из YAML-конфига и набора SQL-файлов.
 * При необходимости materialize'ит SQL references во временную директорию перед разбором `AppConfig`.
 */
class RuntimeConfigSnapshotFactory(
    private val configLoader: ConfigLoader = ConfigLoader(),
) {
    private val workingCopySupport = RuntimeConfigWorkingCopySupport(configLoader)

    fun createSnapshot(
        moduleCode: String?,
        moduleTitle: String?,
        configText: String,
        sqlFiles: Map<String, String>,
        launchSourceKind: String,
        configLocation: String,
        executionSnapshotId: String? = null,
        fallbackSqlResolver: (String) -> String? = { null },
    ): RuntimeModuleSnapshot {
        val tempDir = Files.createTempDirectory("datapool-ui-runtime-${moduleCode ?: "module"}-")
        val tempConfig = workingCopySupport.prepareWorkingCopy(
            configText = configText,
            sqlFiles = sqlFiles,
            tempDir = tempDir,
            fallbackSqlResolver = fallbackSqlResolver,
        )
        val appConfig = configLoader.load(tempConfig)
        return RuntimeModuleSnapshot(
            moduleCode = moduleCode,
            moduleTitle = moduleTitle,
            configYaml = configText,
            sqlFiles = sqlFiles.toSortedMap(),
            appConfig = appConfig,
            launchSourceKind = launchSourceKind,
            executionSnapshotId = executionSnapshotId,
            configLocation = configLocation,
        )
    }
}
