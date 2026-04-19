package com.sbrf.lt.platform.ui.server

import io.ktor.server.application.ApplicationCall
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

internal data class SqlConsoleExecutionPaths(
    val cleanupDir: Path,
    val credentialsPath: Path?,
)

internal fun ApplicationCall.requireSqlConsoleExecutionId(): String =
    requireRouteParam("id")

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
