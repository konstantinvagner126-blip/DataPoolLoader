package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.platform.ui.model.DatabaseRunStartResponse
import com.sbrf.lt.platform.ui.module.DatabaseModuleRegistryOperations
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Оркестрирует запуск DB-модуля и запись run-history в PostgreSQL registry.
 */
open class DatabaseModuleRunService(
    private val databaseModuleStore: DatabaseModuleRegistryOperations,
    private val executionSource: DatabaseModuleExecutionSource,
    private val runExecutionStore: DatabaseRunExecutionStore,
    private val runQueryStore: DatabaseRunQueryStore,
    private val applicationRunner: ApplicationRunner = ApplicationRunner(),
    private val credentialsProvider: UiCredentialsProvider,
    private val activeRunRegistry: DatabaseModuleActiveRunRegistry = DatabaseModuleActiveRunRegistry(),
    private val executor: ExecutorService = Executors.newCachedThreadPool(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
) : DatabaseModuleRunOperations {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val startSupport = DatabaseModuleRunStartSupport(
        databaseModuleStore = databaseModuleStore,
        executionSource = executionSource,
        runExecutionStore = runExecutionStore,
        credentialsProvider = credentialsProvider,
    )
    private val eventSupport = DatabaseModuleRunEventSupport(
        runExecutionStore = runExecutionStore,
        objectMapper = objectMapper,
        logger = logger,
    )
    private val executionSupport = DatabaseModuleRunExecutionSupport(
        applicationRunner = applicationRunner,
        credentialsProvider = credentialsProvider,
        eventSupport = eventSupport,
        logger = logger,
    )
    private val querySupport = DatabaseModuleRunQuerySupport(
        runQueryStore = runQueryStore,
        activeRunRegistry = activeRunRegistry,
    )
    private val submissionSupport = DatabaseModuleRunSubmissionSupport(
        activeRunRegistry = activeRunRegistry,
        executionSupport = executionSupport,
        executor = executor,
    )

    override fun startRun(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): DatabaseRunStartResponse {
        val context = startSupport.prepareStartContext(
            moduleCode = moduleCode,
            actorId = actorId,
            actorSource = actorSource,
            actorDisplayName = actorDisplayName,
            localActiveRunId = activeRunRegistry.currentRunId(moduleCode),
        )
        submissionSupport.submitRun(moduleCode, context)

        return DatabaseRunStartResponse(
            runId = context.runId,
            moduleCode = moduleCode,
            status = "RUNNING",
            requestedAt = context.requestedAt,
            launchSourceKind = context.runtimeSnapshot.launchSourceKind,
            executionSnapshotId = context.runtimeSnapshot.executionSnapshotId ?: error("executionSnapshotId is required for DB run"),
            message = "Запуск DB-модуля '$moduleCode' начат.",
        )
    }

    override fun listRuns(moduleCode: String, limit: Int) =
        querySupport.listRuns(moduleCode, limit)

    override fun loadRunDetails(moduleCode: String, runId: String) =
        querySupport.loadRunDetails(moduleCode, runId)

    override fun activeModuleCodes(): Set<String> = querySupport.activeModuleCodes()
}
