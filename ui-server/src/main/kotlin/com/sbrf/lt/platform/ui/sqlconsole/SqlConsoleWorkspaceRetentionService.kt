package com.sbrf.lt.platform.ui.sqlconsole

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.sbrf.lt.datapool.config.ConfigLoader
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.io.path.exists

internal val DEFAULT_SQL_CONSOLE_WORKSPACE_RETENTION: Duration = Duration.ofDays(30)

internal class SqlConsoleWorkspaceRetentionService(
    private val storageDir: Path,
    private val configLoader: ConfigLoader = ConfigLoader(),
    private val clock: Clock = Clock.systemUTC(),
    private val retention: Duration = DEFAULT_SQL_CONSOLE_WORKSPACE_RETENTION,
) {
    private val lock = Any()
    private val pinnedWorkspaceTokens: MutableSet<String> = linkedSetOf(DEFAULT_SQL_CONSOLE_WORKSPACE_ID)

    fun markWorkspaceAccessed(workspaceId: String? = null) = synchronized(lock) {
        pinnedWorkspaceTokens += workspaceRetentionToken(workspaceId)
    }

    fun cleanupStaleWorkspaceFiles() = synchronized(lock) {
        if (!storageDir.exists()) {
            return
        }
        val cutoff = clock.instant().minus(retention)
        collectWorkspaceCandidates()
            .asSequence()
            .filter { it.workspaceToken != DEFAULT_SQL_CONSOLE_WORKSPACE_ID }
            .filterNot { it.workspaceToken in pinnedWorkspaceTokens }
            .filter { it.lastAccessedAt.isBefore(cutoff) }
            .forEach { candidate ->
                candidate.filePaths.forEach(Files::deleteIfExists)
            }
    }

    private fun collectWorkspaceCandidates(): Collection<SqlConsoleWorkspaceRetentionCandidate> {
        val candidatesByToken = linkedMapOf<String, SqlConsoleWorkspaceRetentionCandidate>()
        Files.list(storageDir).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .forEach { path ->
                    val workspaceToken = parseWorkspaceToken(path.fileName.toString()) ?: return@forEach
                    val fileAccessTime = readWorkspaceAccessTime(path)
                    val current = candidatesByToken[workspaceToken]
                    if (current == null) {
                        candidatesByToken[workspaceToken] = SqlConsoleWorkspaceRetentionCandidate(
                            workspaceToken = workspaceToken,
                            lastAccessedAt = fileAccessTime,
                            filePaths = linkedSetOf(path),
                        )
                    } else {
                        current.filePaths.add(path)
                        if (fileAccessTime.isAfter(current.lastAccessedAt)) {
                            current.lastAccessedAt = fileAccessTime
                        }
                    }
                }
        }
        return candidatesByToken.values
    }

    private fun parseWorkspaceToken(fileName: String): String? =
        when {
            fileName == SQL_CONSOLE_WORKSPACE_STATE_DEFAULT_FILE_NAME -> DEFAULT_SQL_CONSOLE_WORKSPACE_ID
            fileName.startsWith(SQL_CONSOLE_WORKSPACE_STATE_FILE_PREFIX) && fileName.endsWith(".json") ->
                fileName.removePrefix(SQL_CONSOLE_WORKSPACE_STATE_FILE_PREFIX).removeSuffix(".json")
            fileName == SQL_CONSOLE_EXECUTION_HISTORY_STATE_DEFAULT_FILE_NAME -> DEFAULT_SQL_CONSOLE_WORKSPACE_ID
            fileName.startsWith(SQL_CONSOLE_EXECUTION_HISTORY_STATE_FILE_PREFIX) && fileName.endsWith(".json") ->
                fileName.removePrefix(SQL_CONSOLE_EXECUTION_HISTORY_STATE_FILE_PREFIX).removeSuffix(".json")
            else -> null
        }

    private fun readWorkspaceAccessTime(path: Path): Instant =
        readOptionalSqlConsoleStateFile(
            stateFile = path,
            configLoader = configLoader,
            stateClass = SqlConsoleWorkspaceAccessMetadata::class.java,
        )?.lastAccessedAt ?: Files.getLastModifiedTime(path).toInstant()
}

internal fun workspaceRetentionToken(workspaceId: String?): String {
    val normalizedWorkspaceId = normalizeSqlConsoleWorkspaceId(workspaceId)
    return if (normalizedWorkspaceId == DEFAULT_SQL_CONSOLE_WORKSPACE_ID) {
        DEFAULT_SQL_CONSOLE_WORKSPACE_ID
    } else {
        normalizedWorkspaceId.toFileNameToken()
    }
}

private data class SqlConsoleWorkspaceRetentionCandidate(
    val workspaceToken: String,
    var lastAccessedAt: Instant,
    val filePaths: MutableSet<Path>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class SqlConsoleWorkspaceAccessMetadata(
    val lastAccessedAt: Instant? = null,
)

internal const val SQL_CONSOLE_WORKSPACE_STATE_DEFAULT_FILE_NAME = "sql-console-workspace-state.json"
internal const val SQL_CONSOLE_WORKSPACE_STATE_FILE_PREFIX = "sql-console-workspace-state-"
internal const val SQL_CONSOLE_EXECUTION_HISTORY_STATE_DEFAULT_FILE_NAME = "sql-console-execution-history-state.json"
internal const val SQL_CONSOLE_EXECUTION_HISTORY_STATE_FILE_PREFIX = "sql-console-execution-history-state-"
