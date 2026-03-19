package com.sbrf.lt.datapool.config

import java.nio.file.Files
import java.nio.file.Path

object CredentialsFileLocator {
    fun find(
        explicitPath: String? = System.getProperty("credentials.file"),
        startDir: Path = Path.of("").toAbsolutePath().normalize(),
    ): Path? {
        explicitPath?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(Path::of)
            ?.let { return it }

        findInProjectGradle(startDir)?.let { return it }

        val userHome = System.getProperty("user.home")?.trim()?.takeIf { it.isNotEmpty() }?.let(Path::of)
        if (userHome != null) {
            val userGradle = userHome.resolve(".gradle").resolve("credential.properties")
            if (Files.exists(userGradle)) {
                return userGradle
            }
        }

        return null
    }

    private fun findInProjectGradle(startDir: Path): Path? {
        var current = startDir
        while (true) {
            val candidate = current.resolve("gradle").resolve("credential.properties")
            if (Files.exists(candidate)) {
                return candidate
            }
            val parent = current.parent ?: return null
            current = parent
        }
    }
}
