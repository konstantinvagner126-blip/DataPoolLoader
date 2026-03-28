package com.sbrf.lt.platform.ui.sqlconsole

import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class SqlConsoleStateStore(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
) {
    private val stateFile: Path = storageDir.resolve("sql-console-state.json")

    fun load(): PersistedSqlConsoleState {
        if (!stateFile.exists()) {
            return PersistedSqlConsoleState()
        }
        stateFile.inputStream().bufferedReader().use {
            return configLoader.objectMapper()
                .readValue(it, PersistedSqlConsoleState::class.java)
                .normalized()
        }
    }

    fun save(state: PersistedSqlConsoleState) {
        storageDir.createDirectories()
        val normalized = state.normalized()
        val tempFile = storageDir.resolve("sql-console-state.json.tmp")
        tempFile.outputStream().bufferedWriter().use {
            configLoader.objectMapper().writerWithDefaultPrettyPrinter().writeValue(it, normalized)
        }
        Files.move(
            tempFile,
            stateFile,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }
}

data class PersistedSqlConsoleState(
    val draftSql: String = "select 1 as check_value",
    val recentQueries: List<String> = emptyList(),
    val selectedSourceNames: List<String> = emptyList(),
    val pageSize: Int = 50,
) {
    fun normalized(): PersistedSqlConsoleState {
        val normalizedQueries = recentQueries
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(10)
        val normalizedSelectedSources = selectedSourceNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val normalizedPageSize = pageSize.takeIf { it in setOf(25, 50, 100) } ?: 50
        val normalizedDraft = draftSql.ifBlank { "select 1 as check_value" }
        return copy(
            draftSql = normalizedDraft,
            recentQueries = normalizedQueries,
            selectedSourceNames = normalizedSelectedSources,
            pageSize = normalizedPageSize,
        )
    }
}
