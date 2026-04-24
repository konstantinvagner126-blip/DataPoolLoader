package com.sbrf.lt.platform.ui.sqlconsole

import com.fasterxml.jackson.databind.JsonNode
import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

internal fun <T : Any> readOptionalSqlConsoleStateFile(
    stateFile: Path,
    configLoader: ConfigLoader,
    stateClass: Class<T>,
): T? {
    if (!stateFile.exists()) {
        return null
    }
    return try {
        stateFile.inputStream().bufferedReader().use {
            configLoader.objectMapper().readValue(it, stateClass)
        }
    } catch (_: Exception) {
        null
    }
}

internal fun saveSqlConsoleStateFile(
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

internal const val SQL_CONSOLE_LIBRARY_STATE_FILE_NAME = "sql-console-library-state.json"
internal const val SQL_CONSOLE_PREFERENCES_STATE_FILE_NAME = "sql-console-preferences-state.json"

internal fun normalizeLegacySplitPreferencesStateIfNeeded(
    storageDir: Path,
    configLoader: ConfigLoader,
) {
    val preferencesStateFile = storageDir.resolve(SQL_CONSOLE_PREFERENCES_STATE_FILE_NAME)
    if (!preferencesStateFile.exists()) {
        return
    }
    val legacyTree = readOptionalSqlConsoleStateTree(preferencesStateFile, configLoader) ?: return
    if (!legacyTree.isObject || !legacyTree.isLegacySplitPreferencesPayload()) {
        return
    }
    val mapper = configLoader.objectMapper()
    val legacyState = runCatching {
        mapper.treeToValue(legacyTree, LegacySqlConsoleState::class.java)
    }.getOrNull()?.normalized() ?: return
    val libraryStateFile = storageDir.resolve(SQL_CONSOLE_LIBRARY_STATE_FILE_NAME)
    if (!libraryStateFile.exists()) {
        saveSqlConsoleStateFile(
            storageDir = storageDir,
            stateFile = libraryStateFile,
            configLoader = configLoader,
            state = legacyState.toLibraryState(),
        )
    }
    saveSqlConsoleStateFile(
        storageDir = storageDir,
        stateFile = preferencesStateFile,
        configLoader = configLoader,
        state = legacyState.toPreferencesState(),
    )
}

internal fun <T> migrateSqlConsoleStateIfNeeded(
    storageDir: Path,
    shouldMigrate: Boolean,
    legacyStateStore: LegacySqlConsoleStateStore,
    migratedState: T,
    save: (T) -> Unit,
): T {
    if (shouldMigrate) {
        save(migratedState)
        cleanupLegacyCombinedSqlConsoleStateIfMigrated(storageDir, legacyStateStore)
    }
    return migratedState
}

private fun readOptionalSqlConsoleStateTree(
    stateFile: Path,
    configLoader: ConfigLoader,
): JsonNode? {
    if (!stateFile.exists()) {
        return null
    }
    return try {
        stateFile.inputStream().bufferedReader().use {
            configLoader.objectMapper().readTree(it)
        }
    } catch (_: Exception) {
        null
    }
}

private fun JsonNode.isLegacySplitPreferencesPayload(): Boolean =
    has("recentQueries") || has("favoriteQueries") || has("favoriteObjects")
