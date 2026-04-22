package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

internal fun <T : Any> readOptionalRunStateFile(
    stateFile: Path,
    configLoader: ConfigLoader,
    stateClass: Class<T>,
): T? {
    if (!stateFile.exists()) {
        return null
    }
    return stateFile.inputStream().bufferedReader().use {
        configLoader.objectMapper().readValue(it, stateClass)
    }
}

internal fun saveRunStateFile(
    storageDir: Path,
    stateFile: Path,
    configLoader: ConfigLoader,
    state: Any,
) {
    storageDir.createDirectories()
    val tempFile = storageDir.resolve("${stateFile.fileName}.tmp")
    tempFile.outputStream().bufferedWriter().use {
        configLoader.objectMapper().writerWithDefaultPrettyPrinter().writeValue(it, state)
    }
    Files.move(
        tempFile,
        stateFile,
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE,
    )
}

internal fun runStateFileSizeBytes(stateFile: Path): Long =
    if (stateFile.exists()) stateFile.fileSize() else 0L
