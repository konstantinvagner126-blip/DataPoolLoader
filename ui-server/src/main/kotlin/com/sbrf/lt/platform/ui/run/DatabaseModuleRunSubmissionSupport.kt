package com.sbrf.lt.platform.ui.run

import java.util.concurrent.ExecutorService

internal class DatabaseModuleRunSubmissionSupport(
    private val activeRunRegistry: DatabaseModuleActiveRunRegistry,
    private val executionSupport: DatabaseModuleRunExecutionSupport,
    private val executor: ExecutorService,
) {
    fun submitRun(
        moduleCode: String,
        context: DatabaseModuleRunContext,
    ) {
        activeRunRegistry.markActive(moduleCode, context.runId)
        executor.submit {
            try {
                executionSupport.executeRun(
                    moduleCode = moduleCode,
                    context = context,
                )
            } finally {
                activeRunRegistry.clear(moduleCode, context.runId)
            }
        }
    }
}
