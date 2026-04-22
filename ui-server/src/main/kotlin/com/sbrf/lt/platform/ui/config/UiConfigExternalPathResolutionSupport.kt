package com.sbrf.lt.platform.ui.config

import java.nio.file.Path

internal class UiConfigExternalPathResolutionSupport(
    private val packagedSidecarPathProvider: () -> Path?,
) {
    fun resolveExternalConfigPath(): Path? {
        resolveExplicitUiConfigPath()?.let { return it }
        packagedSidecarPathProvider()?.let { return it }
        return resolveExistingDefaultManagedUiConfigPath()
    }
}
