package com.sbrf.lt.datapool.config

import java.nio.file.Files
import java.nio.file.Path

object ProjectRootLocator {
    fun find(start: Path = Path.of("").toAbsolutePath().normalize()): Path? {
        var current = start
        while (true) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }
            val parent = current.parent ?: return null
            current = parent
        }
    }
}
