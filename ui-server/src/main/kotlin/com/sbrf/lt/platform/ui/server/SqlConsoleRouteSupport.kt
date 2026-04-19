package com.sbrf.lt.platform.ui.server

import com.sbrf.lt.datapool.sqlconsole.SqlConsoleTransactionMode
import com.sbrf.lt.platform.ui.model.SqlConsoleQueryRequest
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

internal data class SqlConsoleExecutionPaths(
    val cleanupDir: Path,
    val credentialsPath: Path?,
)

internal inline fun <T> UiServerContext.withSqlConsoleCredentialsPath(
    prefix: String,
    block: (Path?) -> T,
): T {
    val tempDir = createTempDirectory(prefix)
    try {
        val credentialsPath = filesRunService.materializeCredentialsFile(tempDir)
        return block(credentialsPath)
    } finally {
        tempDir.toFile().deleteRecursively()
    }
}

internal fun UiServerContext.createSqlConsoleExecutionPaths(prefix: String): SqlConsoleExecutionPaths {
    val tempDir = createTempDirectory(prefix)
    return try {
        SqlConsoleExecutionPaths(
            cleanupDir = tempDir,
            credentialsPath = filesRunService.materializeCredentialsFile(tempDir),
        )
    } catch (ex: Exception) {
        tempDir.toFile().deleteRecursively()
        throw ex
    }
}

internal fun SqlConsoleQueryRequest.toTransactionMode(): SqlConsoleTransactionMode =
    runCatching { SqlConsoleTransactionMode.valueOf(transactionMode.uppercase()) }
        .getOrDefault(SqlConsoleTransactionMode.AUTO_COMMIT)
