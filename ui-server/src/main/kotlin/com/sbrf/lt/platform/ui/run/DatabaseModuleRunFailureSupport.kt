package com.sbrf.lt.platform.ui.run

import org.slf4j.Logger
import java.time.Instant

internal class DatabaseModuleRunFailureSupport(
    private val runExecutionStore: DatabaseRunExecutionStore,
    private val logger: Logger,
) {
    fun failRun(context: DatabaseModuleRunContext, exception: Throwable) {
        if (!context.runCreated) {
            return
        }
        val errorMessage = exception.message
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: (exception::class.qualifiedName ?: "DB run failed")
        runCatching {
            runExecutionStore.markRunFailed(
                runId = context.runId,
                finishedAt = Instant.now(),
                errorMessage = errorMessage,
            )
        }.onFailure { markFailedError ->
            logger.error(
                "Не удалось перевести DB-run {} в FAILED после ошибки {}: {}",
                context.runId,
                errorMessage,
                markFailedError.message,
                markFailedError,
            )
        }
    }
}
