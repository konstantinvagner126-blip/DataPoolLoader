package com.sbrf.lt.platform.ui.run

import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.datapool.app.ExecutionListener
import org.slf4j.Logger
import java.nio.file.Files

internal class DatabaseModuleRunExecutionSupport(
    private val applicationRunner: ApplicationRunner,
    private val credentialsProvider: UiCredentialsProvider,
    private val eventSupport: DatabaseModuleRunEventSupport,
    private val logger: Logger,
) {
    fun executeRun(
        moduleCode: String,
        context: DatabaseModuleRunContext,
    ) {
        val tempDir = Files.createTempDirectory("datapool-ui-db-run-${moduleCode}-")
        val credentialsPath = credentialsProvider.materializeCredentialsFile(tempDir)
        runCatching {
            applicationRunner.run(
                snapshot = context.runtimeSnapshot,
                credentialsPath = credentialsPath,
                executionListener = ExecutionListener { event ->
                    eventSupport.handleEvent(context, event)
                },
            )
        }.onFailure { ex ->
            logger.error("DB-run {} for module {} failed: {}", context.runId, moduleCode, ex.message, ex)
            eventSupport.failRun(context, ex)
        }
    }
}
