package com.sbrf.lt.platform.ui.config

import java.nio.file.Files
import java.nio.file.Path

internal fun resolveExplicitUiConfigPath(): Path? {
    System.getProperty("datapool.ui.config")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return Path.of(it).toAbsolutePath().normalize() }

    System.getenv("DATAPOOL_UI_CONFIG")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return Path.of(it).toAbsolutePath().normalize() }

    return null
}

internal fun resolveDefaultManagedUiConfigPath(): Path {
    val userHome = System.getProperty("user.home")?.trim()?.takeIf { it.isNotEmpty() }
    return if (userHome != null) {
        Path.of(userHome).resolve(".datapool-loader/ui/application.yml").toAbsolutePath().normalize()
    } else {
        Path.of(".datapool-loader/ui/application.yml").toAbsolutePath().normalize()
    }
}

internal fun resolveExistingDefaultManagedUiConfigPath(): Path? =
    resolveDefaultManagedUiConfigPath().takeIf { Files.exists(it) }

internal fun resolvePackagedUiAppDirectory(
    launcherPath: Path?,
    processCommand: String?,
): Path? {
    val resolvedLauncherPath = launcherPath
        ?: processCommand
            ?.let { Path.of(it).toAbsolutePath().normalize() }
        ?: return null

    val macAppBundle = resolvedLauncherPath.ancestors()
        .firstOrNull { it.fileName?.toString()?.endsWith(".app") == true }
    return if (macAppBundle != null) {
        macAppBundle.parent?.normalize()
    } else {
        resolvedLauncherPath.parent?.normalize()
    }
}

internal fun resolveReadablePackagedSidecarConfigPath(appDir: Path?): Path? {
    if (appDir == null) {
        return null
    }
    val candidates = listOf(
        appDir.resolve("ui-application.yml"),
        appDir.resolve("application.yml"),
    )
    return candidates.firstOrNull { Files.exists(it) }
}

internal fun resolveManagedPackagedSidecarConfigPath(appDir: Path?): Path? =
    appDir?.resolve("ui-application.yml")?.normalize()

private fun Path.ancestors(): Sequence<Path> = sequence {
    var current: Path? = this@ancestors
    while (current != null) {
        yield(current)
        current = current.parent
    }
}
