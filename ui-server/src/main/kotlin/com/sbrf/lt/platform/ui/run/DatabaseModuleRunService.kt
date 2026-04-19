package com.sbrf.lt.platform.ui.run

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sbrf.lt.datapool.app.ApplicationRunner
import com.sbrf.lt.datapool.app.ExecutionEvent
import com.sbrf.lt.datapool.app.ExecutionListener
import com.sbrf.lt.datapool.model.ExecutionStatus
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunDetailsResponse
import com.sbrf.lt.platform.ui.model.DatabaseModuleRunsResponse
import com.sbrf.lt.platform.ui.model.DatabaseRunStartResponse
import com.sbrf.lt.platform.ui.module.DatabaseModuleRegistryOperations
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.io.path.exists
import kotlin.io.path.readText

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
    private val executor: ExecutorService = Executors.newCachedThreadPool(),
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
) : DatabaseModuleRunOperations {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val activeRunIdsByModule = ConcurrentHashMap<String, String>()
    }

    override fun startRun(
        moduleCode: String,
        actorId: String,
        actorSource: String,
        actorDisplayName: String?,
    ): DatabaseRunStartResponse {
        recoverOrphanRuns(moduleCode)
        require(!runExecutionStore.hasActiveRun(moduleCode)) {
            "Для модуля '$moduleCode' уже выполняется DB-запуск. Дождитесь его завершения."
        }

        val details = databaseModuleStore.loadModuleDetails(moduleCode, actorId, actorSource)
        validateCredentialsBeforeRun(details.module.configText)

        val runtimeSnapshot = executionSource.prepareExecution(
            moduleCode = moduleCode,
            actorId = actorId,
            actorSource = actorSource,
            actorDisplayName = actorDisplayName,
        )
        val requestedAt = Instant.now()
        val context = DatabaseModuleRunContext(
            runId = UUID.randomUUID().toString(),
            runtimeSnapshot = runtimeSnapshot,
            actorId = actorId,
            actorSource = actorSource,
            actorDisplayName = actorDisplayName,
            requestedAt = requestedAt,
            sourceOrder = runtimeSnapshot.appConfig.sources.mapIndexed { index, source -> source.name to index }.toMap(),
            targetStatus = if (runtimeSnapshot.appConfig.target.enabled) "PENDING" else "NOT_ENABLED",
        )

        activeRunIdsByModule[moduleCode] = context.runId
        executor.submit {
            try {
                val tempDir = Files.createTempDirectory("datapool-ui-db-run-${moduleCode}-")
                val credentialsPath = credentialsProvider.materializeCredentialsFile(tempDir)
                runCatching {
                    applicationRunner.run(
                        snapshot = runtimeSnapshot,
                        credentialsPath = credentialsPath,
                        executionListener = ExecutionListener { event ->
                            handleEvent(context, event)
                        },
                    )
                }.onFailure { ex ->
                    logger.error("DB-run {} for module {} failed: {}", context.runId, moduleCode, ex.message, ex)
                    failRun(context, ex)
                }
            } finally {
                activeRunIdsByModule.remove(moduleCode, context.runId)
            }
        }

        return DatabaseRunStartResponse(
            runId = context.runId,
            moduleCode = moduleCode,
            status = "RUNNING",
            requestedAt = requestedAt,
            launchSourceKind = runtimeSnapshot.launchSourceKind,
            executionSnapshotId = runtimeSnapshot.executionSnapshotId ?: error("executionSnapshotId is required for DB run"),
            message = "Запуск DB-модуля '$moduleCode' начат.",
        )
    }

    override fun listRuns(moduleCode: String, limit: Int): DatabaseModuleRunsResponse =
        DatabaseModuleRunsResponse(
            moduleCode = moduleCode,
            runs = runQueryStore.listRuns(moduleCode, limit),
        )

    override fun loadRunDetails(moduleCode: String, runId: String): DatabaseModuleRunDetailsResponse =
        runQueryStore.loadRunDetails(moduleCode, runId)

    override fun activeModuleCodes(): Set<String> =
        buildSet {
            addAll(runQueryStore.activeModuleCodes())
            addAll(activeRunIdsByModule.keys)
        }

    private fun validateCredentialsBeforeRun(configText: String) {
        val requirement = analyzeCredentialRequirements(configText, credentialsProvider.currentProperties())
        if (!requirement.requiresCredentials) {
            return
        }
        val status = credentialsProvider.currentCredentialsStatus()
        require(requirement.ready) {
            buildMissingCredentialValuesMessage(
                subjectLabel = "DB-модуля",
                missingKeys = requirement.missingKeys,
                credentialsStatus = status,
            )
        }
    }

    private fun recoverOrphanRuns(moduleCode: String) {
        val localActiveRunId = activeRunIdsByModule[moduleCode]
        runExecutionStore.activeRunIds(moduleCode)
            .filter { it != localActiveRunId }
            .forEach { runId ->
                runExecutionStore.markRunFailed(
                    runId = runId,
                    finishedAt = Instant.now(),
                    errorMessage = "DB-запуск был прерван до завершения и восстановлен как FAILED при следующем старте UI.",
                )
            }
    }

    private fun handleEvent(context: DatabaseModuleRunContext, event: ExecutionEvent) {
        synchronized(context) {
            when (event) {
                is com.sbrf.lt.datapool.app.RunStartedEvent -> {
                    runExecutionStore.createRun(context, event.timestamp, event.outputDir)
                    context.runCreated = true
                    appendEvent(context, "PREPARE", "RUN_CREATED", "INFO", null, "DB-запуск создан.", event)
                }
                is com.sbrf.lt.datapool.app.SourceExportStartedEvent -> {
                    context.sourceStates.getOrPut(event.sourceName) { DatabaseRunSourceState() }.status = "RUNNING"
                    runExecutionStore.markSourceStarted(context.runId, event.sourceName, event.timestamp)
                    appendEvent(context, "SOURCE", "SOURCE_STARTED", "INFO", event.sourceName, "Начата выгрузка источника ${event.sourceName}.", event)
                }
                is com.sbrf.lt.datapool.app.SourceExportProgressEvent -> {
                    val sourceState = context.sourceStates.getOrPut(event.sourceName) { DatabaseRunSourceState() }
                    sourceState.status = "RUNNING"
                    sourceState.exportedRowCount = event.rowCount
                    runExecutionStore.updateSourceProgress(
                        runId = context.runId,
                        sourceName = event.sourceName,
                        timestamp = event.timestamp,
                        exportedRowCount = event.rowCount,
                    )
                    appendEvent(
                        context = context,
                        stage = "SOURCE",
                        eventType = "SOURCE_PROGRESS",
                        severity = "INFO",
                        sourceName = event.sourceName,
                        message = "Источник ${event.sourceName}: выгружено ${event.rowCount} строк.",
                        event = event,
                    )
                }
                is com.sbrf.lt.datapool.app.SourceExportFinishedEvent -> {
                    val sourceState = context.sourceStates.getOrPut(event.sourceName) { DatabaseRunSourceState() }
                    sourceState.status = if (event.status == ExecutionStatus.SUCCESS) "SUCCESS" else "FAILED"
                    sourceState.exportedRowCount = event.rowCount
                    runExecutionStore.markSourceFinished(
                        runId = context.runId,
                        sourceName = event.sourceName,
                        status = sourceState.status,
                        finishedAt = event.timestamp,
                        exportedRowCount = event.rowCount,
                        errorMessage = event.errorMessage,
                    )
                    event.outputFile?.let { outputFile ->
                        rememberArtifact(context, Path.of(outputFile), "SOURCE_OUTPUT", event.sourceName)
                    }
                    appendEvent(
                        context = context,
                        stage = "SOURCE",
                        eventType = if (sourceState.status == "FAILED") "SOURCE_FAILED" else "SOURCE_FINISHED",
                        severity = if (sourceState.status == "FAILED") "ERROR" else "SUCCESS",
                        sourceName = event.sourceName,
                        message = if (sourceState.status == "FAILED") {
                            "Источник ${event.sourceName} завершился ошибкой: ${event.errorMessage ?: "неизвестная ошибка"}."
                        } else {
                            "Источник ${event.sourceName} выгружен: ${event.rowCount} строк."
                        },
                        event = event,
                    )
                }
                is com.sbrf.lt.datapool.app.SourceSchemaMismatchEvent -> {
                    context.sourceStates.getOrPut(event.sourceName) { DatabaseRunSourceState() }.status = "SKIPPED"
                    runExecutionStore.markSourceSkipped(
                        runId = context.runId,
                        sourceName = event.sourceName,
                        finishedAt = event.timestamp,
                        message = "Несовпадение схемы: ожидались ${event.expectedColumns}, получены ${event.actualColumns}",
                    )
                    appendEvent(
                        context,
                        "SOURCE",
                        "SOURCE_FAILED",
                        "WARNING",
                        event.sourceName,
                        "Источник ${event.sourceName} исключен из merge из-за несовпадения схемы.",
                        event,
                    )
                }
                is com.sbrf.lt.datapool.app.MergeStartedEvent -> {
                    appendEvent(context, "MERGE", "MERGE_STARTED", "INFO", null, "Начато объединение результатов.", event)
                }
                is com.sbrf.lt.datapool.app.MergeFinishedEvent -> {
                    runExecutionStore.updateMergedRowCount(context.runId, event.rowCount)
                    event.sourceCounts.forEach { (sourceName, mergedRowCount) ->
                        context.sourceStates.getOrPut(sourceName) { DatabaseRunSourceState(status = "SUCCESS") }.mergedRowCount = mergedRowCount
                        runExecutionStore.updateSourceMergedRows(context.runId, sourceName, mergedRowCount)
                    }
                    rememberArtifact(context, Path.of(event.outputFile), "MERGED_OUTPUT", "merged")
                    appendEvent(context, "MERGE", "MERGE_FINISHED", "SUCCESS", null, "Объединение завершено: ${event.rowCount} строк.", event)
                }
                is com.sbrf.lt.datapool.app.TargetImportStartedEvent -> {
                    context.targetStatus = "RUNNING"
                    runExecutionStore.updateTargetStatus(context.runId, "RUNNING", event.table, null)
                    appendEvent(context, "TARGET", "TARGET_STARTED", "INFO", null, "Начата загрузка в target ${event.table}.", event)
                }
                is com.sbrf.lt.datapool.app.TargetImportFinishedEvent -> {
                    context.targetStatus = when (event.status) {
                        ExecutionStatus.SUCCESS -> "SUCCESS"
                        ExecutionStatus.SKIPPED -> "SKIPPED"
                        else -> "FAILED"
                    }
                    context.targetRowsLoaded = event.rowCount
                    runExecutionStore.updateTargetStatus(context.runId, context.targetStatus, event.table, event.rowCount)
                    appendEvent(
                        context = context,
                        stage = "TARGET",
                        eventType = if (context.targetStatus == "FAILED") "TARGET_FAILED" else "TARGET_FINISHED",
                        severity = when (context.targetStatus) {
                            "FAILED" -> "ERROR"
                            "SUCCESS" -> "SUCCESS"
                            else -> "INFO"
                        },
                        sourceName = null,
                        message = if (context.targetStatus == "FAILED") {
                            "Загрузка в target ${event.table} завершилась ошибкой: ${event.errorMessage ?: "неизвестная ошибка"}."
                        } else {
                            "Загрузка в target ${event.table} завершена."
                        },
                        event = event,
                    )
                }
                is com.sbrf.lt.datapool.app.RunFinishedEvent -> {
                    val summaryJson = loadSummaryJson(event.summaryFile)
                    event.summaryFile?.let { rememberArtifact(context, Path.of(it), "SUMMARY_JSON", "summary") }
                    val successfulSourceCount = context.sourceStates.values.count { it.status == "SUCCESS" }
                    val failedSourceCount = context.sourceStates.values.count { it.status == "FAILED" }
                    val skippedSourceCount = context.sourceStates.values.count { it.status == "SKIPPED" }
                    val finalStatus = when {
                        event.status == ExecutionStatus.FAILED -> "FAILED"
                        failedSourceCount > 0 || skippedSourceCount > 0 -> "SUCCESS_WITH_WARNINGS"
                        else -> "SUCCESS"
                    }
                    runExecutionStore.finishRun(
                        runId = context.runId,
                        finishedAt = event.timestamp,
                        status = finalStatus,
                        mergedRowCount = event.mergedRowCount.takeIf { it > 0L },
                        successfulSourceCount = successfulSourceCount,
                        failedSourceCount = failedSourceCount,
                        skippedSourceCount = skippedSourceCount,
                        targetStatus = context.targetStatus,
                        targetTableName = context.runtimeSnapshot.appConfig.target.table.takeIf { it.isNotBlank() },
                        targetRowsLoaded = context.targetRowsLoaded,
                        summaryJson = summaryJson,
                        errorMessage = event.errorMessage,
                    )
                    appendEvent(
                        context = context,
                        stage = "RUN",
                        eventType = if (finalStatus == "FAILED") "RUN_FAILED" else "RUN_FINISHED",
                        severity = if (finalStatus == "FAILED") "ERROR" else "SUCCESS",
                        sourceName = null,
                        message = if (finalStatus == "FAILED") {
                            "DB-запуск завершился ошибкой: ${event.errorMessage ?: "неизвестная ошибка"}."
                        } else {
                            "DB-запуск завершен."
                        },
                        event = event,
                    )
                }
                is com.sbrf.lt.datapool.app.OutputCleanupEvent -> {
                    context.artifactRefsByFileName[event.fileName]?.let { ref ->
                        runExecutionStore.markArtifactDeleted(context.runId, ref.artifactKind, ref.artifactKey)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun rememberArtifact(context: DatabaseModuleRunContext, path: Path, artifactKind: String, artifactKey: String) {
        val storageStatus = if (path.exists()) "PRESENT" else "MISSING"
        val fileSize = if (path.exists()) runExecutionStore.fileSize(path) else null
        runExecutionStore.upsertArtifact(
            runId = context.runId,
            artifactKind = artifactKind,
            artifactKey = artifactKey,
            filePath = path.toString(),
            storageStatus = storageStatus,
            fileSizeBytes = fileSize,
            contentHash = null,
        )
        context.artifactRefsByFileName[path.fileName.toString()] = DatabaseRunArtifactRef(
            artifactKind = artifactKind,
            artifactKey = artifactKey,
        )
    }

    private fun appendEvent(
        context: DatabaseModuleRunContext,
        stage: String,
        eventType: String,
        severity: String,
        sourceName: String?,
        message: String,
        event: ExecutionEvent,
    ) {
        runCatching {
            val payload = objectMapper.convertValue(event, MutableMap::class.java)
                .mapKeys { it.key.toString() }
                .toMutableMap()
            payload["type"] = event.javaClass.simpleName
            runExecutionStore.appendEvent(
                runId = context.runId,
                seqNo = context.nextSeqNo++,
                stage = stage,
                eventType = eventType,
                severity = severity,
                sourceName = sourceName,
                message = message,
                payload = payload,
            )
        }.onFailure { ex ->
            logger.warn(
                "Не удалось записать DB run event: runId={}, stage={}, eventType={}, reason={}",
                context.runId,
                stage,
                eventType,
                ex.message,
            )
        }
    }

    private fun loadSummaryJson(summaryFile: String?): String {
        if (summaryFile.isNullOrBlank()) {
            return "{}"
        }
        val path = Path.of(summaryFile)
        if (!path.exists()) {
            return "{}"
        }
        return runCatching {
            objectMapper.readTree(path.readText()).toString()
        }.getOrDefault("{}")
    }

    private fun failRun(context: DatabaseModuleRunContext, exception: Throwable) {
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
