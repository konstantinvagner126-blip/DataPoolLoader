package com.sbrf.lt.platform.ui.config

import com.sbrf.lt.datapool.config.ProjectRootLocator
import java.nio.file.Files
import java.nio.file.Path

internal class UiConfigPersistencePathSupport(
    private val uiConfigLoader: UiConfigLoader,
    private val startDir: Path,
) {
    fun resolveEditableConfigPath(): Path? {
        uiConfigLoader.resolveExternalConfigPath()?.let { return it }

        val projectRoot = ProjectRootLocator.find(startDir) ?: return null
        val devConfig = projectRoot.resolve("ui-server/src/main/resources/application.yml").normalize()
        return devConfig.takeIf { Files.exists(it) }
    }

    fun resolveManagedExternalConfigPath(packagedSidecarPath: Path?): Path {
        uiConfigLoader.resolveExternalConfigPath()?.let { return it }
        packagedSidecarPath?.let { return it }
        return resolveDefaultManagedUiConfigPath()
    }
}
